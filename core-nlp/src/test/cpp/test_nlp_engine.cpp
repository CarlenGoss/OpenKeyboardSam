#include "../../main/cpp/nlp_engine.h"
#include <cassert>
#include <iostream>

int main() {
    openkeyboardsam::nlp::NlpEngine engine;

    bool loadedEs = engine.loadDictionaryFile("core-nlp/src/main/assets/dicts/es.dict");
    assert(loadedEs);

    auto res1 = engine.predict("tecl", 3);
    assert(!res1.empty());
    assert(res1[0].word == "teclado");

    auto resFuzzy = engine.predict("tecldo", 3);
    assert(!resFuzzy.empty());
    assert(resFuzzy[0].word == "teclado");

    auto resSwipe = engine.decodeSwipe("hgfoiujklma", 3);
    // "hola" is a subsequence of "hgfoiujklma"
    bool foundHola = false;
    for (const auto& s : resSwipe) {
        if (s.word == "hola") foundHola = true;
    }
    assert(foundHola);

    engine.learnWord("OpenKeyboardSam");
    auto resLearned = engine.predict("openk", 3);
    assert(!resLearned.empty());
    assert(resLearned[0].word == "openkeyboardsam");

    std::cout << "All C++ NlpEngine tests passed!" << std::endl;
    return 0;
}
