package org.allenai.qapra.babi

import org.allenai.parse.ParsedSentence
import org.allenai.parse.StanfordParser
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

class DataProcessor(fileUtil: FileUtil = new FileUtil) {

  val parser = new StanfordParser

  // TODO(matt): these should probably be made into public fields in the Dataset object.
  val intraEdgeSeparator = "^,^"
  val interEdgeSeparator = " ### "

  def createSplit(question_file: String, out_file: String) {
    val questions = extractQuestions(question_file)
    val instances = questions.zipWithIndex.flatMap(question_idx => {
      val question = question_idx._1
      val index = question_idx._2 + 1
      val source = getSourceNodeFromQuestion(question._1, index)
      val parsedHistory = question._3.map(sentence => parser.parseSentence(sentence))
      val candidates = getCandidatesFromHistory(parsedHistory)
      val questionSentences = candidates.map(convertQuestionAnswerToSentence(question._1))
      val instanceGraphs = questionSentences.map(getGraphFromQuestion(parsedHistory, index))
      candidates.zip(instanceGraphs).map(candidateGraph => {
        val candidate = candidateGraph._1
        val graph = candidateGraph._2
        val isPositive = candidate == question._2
        val instance = (source, s"Q${index}:${candidate}", isPositive)
        (instance, graph)
      })
    })
    val writer = fileUtil.getFileWriter(out_file)
    instances.foreach(instanceGraph => {
      val instance = instanceGraph._1
      val graph = instanceGraph._2
      writer.write(instance._1)
      writer.write("\t")
      writer.write(instance._2)
      writer.write("\t")
      writer.write(if (instance._3) "1" else "-1")
      writer.write("\t")
      val graphStr = graph.map(edgeToString).mkString(interEdgeSeparator)
      writer.write(graphStr)
      writer.write("\n")
    })
    writer.close()
  }

  def edgeToString(edge: (String, String, String)): String = {
    edge._1 + intraEdgeSeparator + edge._2 + intraEdgeSeparator + edge._3
  }

  def extractQuestions(filename: String): Seq[(String, String, Seq[String])] = {
    val lines = fileUtil.readLinesFromFile(filename).asScala
    val history = new mutable.ListBuffer[String]
    val questions = new mutable.ListBuffer[(String, String, Seq[String])]
    for (line <- lines) {
      val fields = line.split(" ", 2)
      val index = fields(0).toInt
      if (index == 1) {
        history.clear
      }
      val sentence = fields(1)
      val sentenceFields = sentence.split("\t")
      if (sentenceFields.size == 1) {
        history += sentence
      } else {
        val question = sentenceFields(0).trim
        val answer = sentenceFields(1)
        questions += Tuple3(question, answer, history.clone.toSeq)
      }
    }
    questions.toSeq
  }

  // TODO(matt): this is potentially question-dependent, and could use something less hackish here.
  // For now we just take the first noun we find.
  def getSourceNodeFromQuestion(question: String, index: Int): String = {
    val parsed = parser.parseSentence(question)
    val nouns = parsed.getPosTags.filter(_.posTag.contains("NN"))
    s"Q${index}:${nouns(0).word}"
  }

  // TODO(matt): this is probably question-dependent.  For now, we're just getting all of the
  // nouns.
  def getCandidatesFromHistory(history: Seq[ParsedSentence]): Set[String] = {
    val candidates = new mutable.HashSet[String]
    history.foreach(sentence => {
      sentence.getPosTags.map(taggedWord => {
        if (taggedWord.posTag.contains("NN")) {
          candidates += taggedWord.word
        }
      })
    })
    candidates.toSet
  }

  // TODO(matt): this is potentially question-dependent, and for now I'm just going to make this a
  // major hack, hard-coding conversions from various question types that I know are in the babi
  // dataset.
  def convertQuestionAnswerToSentence(_question: String)(answer: String): String = {
    val question = _question.substring(0, _question.size - 1)
    if (question.split(" ").size == 3 && question.startsWith("Where is ")) {
      question.split(" ").last + " is in the " + answer + "."
    } else {
      throw new RuntimeException("This hackish method not finished yet!")
    }
  }

  type Edge = (String, String, String)
  def getGraphFromQuestion(history: Seq[ParsedSentence], questionIndex: Int)
      (questionSentence: String): Seq[Edge] = {
    val edges = new mutable.ListBuffer[Edge]

    // Some processing of the question sentence, as it's an important part of the graph.
    val parsedQuestion = parser.parseSentence(questionSentence)
    val questionWords = parsedQuestion.getPosTags.map(_.word).toSet
    val lastOccurrences = new mutable.HashMap[String, String].withDefaultValue(null)

    // Now, first we add the dependency edges from the question sentence to the graph.
    parsedQuestion.getDependencies.foreach(dependency => {
      if (dependency.label != "root") {
        edges += Tuple3(s"Q${questionIndex}:${dependency.head}", dependency.label,
          s"Q${questionIndex}:${dependency.dependent}")
      }
    })

    // Now we add the dependency edges from all of the sentences in the history.
    history.zipWithIndex.foreach(sentence_idx => {
      val index = sentence_idx._2 + 1
      val sentence = sentence_idx._1
      sentence.getDependencies.foreach(dep => {
        if (dep.label != "root") {
          edges += Tuple3(s"${index}:${dep.head}", dep.label, s"${index}:${dep.dependent}")
        }
      })
      sentence.getPosTags.foreach(posTag => {
        val word = posTag.word
        if (questionWords.contains(word)) {
          edges += Tuple3(s"Q${questionIndex}:${word}", "instance", s"${index}:${word}")
          if (lastOccurrences(word) != null) {
            edges += Tuple3(s"${index}:${word}", "last instance", lastOccurrences(word))
          }
          lastOccurrences(word) = s"${index}:${word}"
        }
      })
    })

    // Lastly, we add the remaining "last instance" edges.
    questionWords.foreach(word => {
      if (lastOccurrences(word) != null) {
        edges += Tuple3(s"Q${questionIndex}:${word}", "last instance", lastOccurrences(word))
      }
    })

    edges.toSeq
  }
}
