#include "nlp_engine.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace openkeyboardsam::nlp {

namespace {
constexpr size_t kMaxUserWords = 2000;
}

NlpEngine::~NlpEngine() {
    unloadDictionary();
}

void NlpEngine::unloadDictionary() {
    entries_.clear();
    entries_.shrink_to_fit();
    if (mappedData_ != nullptr && mappedData_ != MAP_FAILED) {
        munmap(const_cast<char*>(mappedData_), mappedSize_);
    }
    mappedData_ = nullptr;
    mappedSize_ = 0;
    if (fd_ >= 0) {
        close(fd_);
        fd_ = -1;
    }
}

bool NlpEngine::loadDictionaryFile(const std::string& filePath) {
    unloadDictionary();

    fd_ = open(filePath.c_str(), O_RDONLY);
    if (fd_ < 0) return false;

    struct stat st {};
    if (fstat(fd_, &st) != 0 || st.st_size <= 0) {
        unloadDictionary();
        return false;
    }

    mappedSize_ = static_cast<size_t>(st.st_size);
    void* ptr = mmap(nullptr, mappedSize_, PROT_READ, MAP_PRIVATE, fd_, 0);
    if (ptr == MAP_FAILED) {
        unloadDictionary();
        return false;
    }
    mappedData_ = static_cast<const char*>(ptr);

    // Parse line offsets into compact DictEntry structs (no string heap allocations!)
    size_t pos = 0;
    while (pos < mappedSize_) {
        size_t lineEnd = pos;
        while (lineEnd < mappedSize_ && mappedData_[lineEnd] != '\n' && mappedData_[lineEnd] != '\r') {
            ++lineEnd;
        }
        if (lineEnd > pos) {
            size_t tabPos = pos;
            while (tabPos < lineEnd && mappedData_[tabPos] != '\t') {
                ++tabPos;
            }
            uint16_t wordLen = static_cast<uint16_t>(std::min<size_t>(tabPos - pos, 65535));
            uint32_t freq = 100;
            if (tabPos + 1 < lineEnd) {
                freq = static_cast<uint32_t>(std::strtoul(mappedData_ + tabPos + 1, nullptr, 10));
                if (freq == 0) freq = 1;
            }
            if (wordLen > 0) {
                entries_.push_back(DictEntry{static_cast<uint32_t>(pos), wordLen, freq});
            }
        }
        pos = lineEnd + 1;
    }

    return true;
}

std::string_view NlpEngine::wordView(const DictEntry& entry) const {
    return std::string_view(mappedData_ + entry.offset, entry.length);
}

std::string NlpEngine::toLowerAscii(std::string_view input) {
    std::string out;
    out.reserve(input.size());
    for (unsigned char c : input) {
        out.push_back(static_cast<char>(std::tolower(c)));
    }
    return out;
}

int NlpEngine::boundedEditDistance(std::string_view a, std::string_view b, int maxDist) {
    const int n = static_cast<int>(a.size());
    const int m = static_cast<int>(b.size());
    if (std::abs(n - m) > maxDist) return maxDist + 1;

    std::vector<int> prev(m + 1), curr(m + 1);
    for (int j = 0; j <= m; ++j) prev[j] = j;

    for (int i = 1; i <= n; ++i) {
        curr[0] = i;
        int rowMin = curr[0];
        for (int j = 1; j <= m; ++j) {
            int cost = (std::tolower(static_cast<unsigned char>(a[i - 1])) ==
                        std::tolower(static_cast<unsigned char>(b[j - 1]))) ? 0 : 1;
            curr[j] = std::min({prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost});
            rowMin = std::min(rowMin, curr[j]);
        }
        if (rowMin > maxDist) return maxDist + 1;
        std::swap(prev, curr);
    }
    return prev[m];
}

bool NlpEngine::isSubsequence(std::string_view word, std::string_view seq) {
    if (word.empty() || seq.size() < 2) return false;
    auto eq = [](char a, char b) {
        return std::tolower(static_cast<unsigned char>(a)) ==
               std::tolower(static_cast<unsigned char>(b));
    };
    if (!eq(word.front(), seq.front()) || !eq(word.back(), seq.back())) {
        return false;
    }
    size_t wi = 0;
    for (size_t si = 0; si < seq.size() && wi < word.size(); ++si) {
        if (eq(word[wi], seq[si])) {
            ++wi;
        }
    }
    return wi == word.size();
}

std::vector<Suggestion> NlpEngine::predict(const std::string& prefix, size_t maxResults) const {
    if (prefix.empty() || maxResults == 0) return {};

    const std::string lowerPrefix = toLowerAscii(prefix);
    std::vector<Suggestion> candidates;
    candidates.reserve(16);

    // 1. User-learned words boost
    for (const auto& [uWord, uFreq] : userFreqs_) {
        if (uWord.size() >= lowerPrefix.size() &&
            uWord.compare(0, lowerPrefix.size(), lowerPrefix) == 0) {
            candidates.push_back(Suggestion{uWord, uFreq * 10 + 50000});
        }
    }

    // 2. Prefix & fuzzy match on mmap dictionary
    for (const auto& entry : entries_) {
        std::string_view w = wordView(entry);
        if (w.size() >= lowerPrefix.size()) {
            bool isPrefix = true;
            for (size_t i = 0; i < lowerPrefix.size(); ++i) {
                if (std::tolower(static_cast<unsigned char>(w[i])) != lowerPrefix[i]) {
                    isPrefix = false;
                    break;
                }
            }
            if (isPrefix) {
                uint32_t exactBonus = (w.size() == lowerPrefix.size()) ? 100000 : 0;
                candidates.push_back(Suggestion{std::string(w), entry.freq + exactBonus});
                continue;
            }
        }

        // Fuzzy autocorrect for words >= 3 chars
        if (lowerPrefix.size() >= 3 && std::abs(static_cast<int>(w.size()) - static_cast<int>(lowerPrefix.size())) <= 1) {
            int dist = boundedEditDistance(lowerPrefix, w, 1);
            if (dist <= 1) {
                candidates.push_back(Suggestion{std::string(w), entry.freq / 2});
            }
        }
    }

    std::sort(candidates.begin(), candidates.end(), [](const Suggestion& a, const Suggestion& b) {
        return a.score > b.score;
    });

    // Deduplicate while preserving top score order
    std::vector<Suggestion> result;
    result.reserve(maxResults);
    for (const auto& item : candidates) {
        bool dup = false;
        for (const auto& existing : result) {
            if (existing.word == item.word) {
                dup = true;
                break;
            }
        }
        if (!dup) {
            result.push_back(item);
            if (result.size() >= maxResults) break;
        }
    }
    return result;
}

std::vector<Suggestion> NlpEngine::decodeSwipe(const std::string& keySequence, size_t maxResults) const {
    if (keySequence.size() < 2 || maxResults == 0) return {};

    std::vector<Suggestion> candidates;
    for (const auto& entry : entries_) {
        std::string_view w = wordView(entry);
        if (w.size() >= 2 && isSubsequence(w, keySequence)) {
            uint32_t lenBonus = static_cast<uint32_t>(w.size() * 200);
            candidates.push_back(Suggestion{std::string(w), entry.freq + lenBonus});
        }
    }

    std::sort(candidates.begin(), candidates.end(), [](const Suggestion& a, const Suggestion& b) {
        return a.score > b.score;
    });

    if (candidates.size() > maxResults) {
        candidates.resize(maxResults);
    }
    return candidates;
}

void NlpEngine::learnWord(const std::string& word) {
    if (word.size() < 2 || word.size() > 32) return;
    if (userFreqs_.size() >= kMaxUserWords) {
        // Evict lowest-frequency entry to bound RAM strictly
        auto minIt = std::min_element(userFreqs_.begin(), userFreqs_.end(),
            [](const auto& a, const auto& b) { return a.second < b.second; });
        if (minIt != userFreqs_.end()) {
            userFreqs_.erase(minIt);
        }
    }
    userFreqs_[toLowerAscii(word)] += 1;
}

void NlpEngine::trimMemory() {
    entries_.shrink_to_fit();
}

} // namespace openkeyboardsam::nlp
