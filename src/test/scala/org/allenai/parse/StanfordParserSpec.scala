package org.allenai.parse

import org.scalatest._

class StanfordParserSpec extends FlatSpecLike with Matchers {

  val parser = new StanfordParser

  "parseSentence" should "give correct dependencies and part of speech tags" in {
    val sentence = "People eat good food."
    val parse = parser.parseSentence(sentence)

    val dependencies = parse.getDependencies
    dependencies.size should be(4)
    dependencies.toSet should be(Set(
      Dependency("eat", "People", "nsubj"),
      Dependency("eat", "food", "dobj"),
      Dependency("food", "good", "amod"),
      Dependency("ROOT", "eat", "root")))

    val posTags = parse.getPosTags
    posTags.size should be(5)
    posTags(0) should be(PartOfSpeech("People", "NNS"))
    posTags(1) should be(PartOfSpeech("eat", "VBP"))
    posTags(2) should be(PartOfSpeech("good", "JJ"))
    posTags(3) should be(PartOfSpeech("food", "NN"))
    posTags(4) should be(PartOfSpeech(".", "."))
  }

  it should "give collapsed dependencies" in {
    val sentence = "Mary went to the store."
    val parse = parser.parseSentence(sentence)

    val dependencies = parse.getDependencies
    dependencies.size should be(4)
    dependencies.toSet should be(Set(
      Dependency("went", "Mary", "nsubj"),
      Dependency("went", "store", "prep_to"),
      Dependency("store", "the", "det"),
      Dependency("ROOT", "went", "root")))

  }
}
