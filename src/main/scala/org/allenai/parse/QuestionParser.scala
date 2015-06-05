package org.allenai.parse

// TODO(matt): the point of this was to replace the wh-phrase in a question with some noun phrase.
// It's not done at all, and I moved the actual parsing into something that's not
// question-specific.
object QuestionParser {
  val parser = new StanfordParser

  def parseQuestion(question: String) = {
    parser.parseSentence(question)
  }

  def test(args: Array[String]) {
    val parse = parseQuestion("On what island is Mt. Pinatubo?")
    println(parse.getDependencies)
  }
}
