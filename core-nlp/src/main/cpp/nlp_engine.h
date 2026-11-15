#pragma once

#include <cstdint>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace openkeyboardsam::nlp {

struct Suggestion {
    std::string word;
    uint32_t score;
};

/**
 * Memory-mapped, zero-heap-bloat NLP engine for prefix prediction,
 * fuzzy autocorrection, and swipe-path decoding.
 */
class NlpEngine {
public:
    NlpEngine() = default;
    ~NlpEngine();

    NlpEngine(const NlpEngine&) = delete;
    NlpEngine& operator=(const NlpEngine&) = delete;

    bool loadDictionaryFile(const std::string& filePath);
    void unloadDictionary();

    std::vector<Suggestion> predict(const std::string& prefix, size_t maxResults = 3) const;
    std::vector<Suggestion> decodeSwipe(const std::string& keySequence, size_t maxResults = 3) const;
    void learnWord(const std::string& word);
    void trimMemory();

private:
    struct DictEntry {
        uint32_t offset;
        uint16_t length;
        uint32_t freq;
    };

    const char* mappedData_ = nullptr;
    size_t mappedSize_ = 0;
    int fd_ = -1;

    std::vector<DictEntry> entries_;
    std::unordered_map<std::string, uint32_t> userFreqs_;

    std::string_view wordView(const DictEntry& entry) const;
    static std::string toLowerAscii(std::string_view input);
    static int boundedEditDistance(std::string_view a, std::string_view b, int maxDist);
    static bool isSubsequence(std::string_view word, std::string_view seq);
};

} // namespace openkeyboardsam::nlp
