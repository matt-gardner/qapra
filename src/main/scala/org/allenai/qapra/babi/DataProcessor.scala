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
    val instances = questions.zipWithIndex.par.flatMap(question_idx => {
      val question = question_idx._1
      val index = question_idx._2 + 1
      val source = getSourceNodeFromQuestion(question._1, index)
      val parsedHistory = question._3.map(sentence => parser.parseSentence(sentence))
      val candidates = getCandidatesFromHistory(parsedHistory).toList
      val questionSentences = candidates.map(convertQuestionAnswerToSentence(question._1))
      val instanceGraphs = questionSentences.map(getGraphFromQuestion(parsedHistory, index))
      candidates.zip(instanceGraphs).map(candidateGraph => {
        val candidate = candidateGraph._1
        val graph = candidateGraph._2
        val isPositive = candidate == question._2
        // TODO(matt): kind of a hack.  I should return the POS tag with the candidate so this
        // isn't such a hack.
        val keep = if (shouldLexicalizeWord("NN")) "KEEP" else "REMOVE"
        val instance = (source, s"Q${index}:${keep}:${candidate}", isPositive)
        (instance, graph)
      })
    }).seq
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
    val keep = if (shouldLexicalizeWord(nouns(0).posTag)) "KEEP" else "REMOVE"
    s"Q${index}:${keep}:${nouns(0).word}"
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

  def shouldLexicalizeWord(posTag: String) = {
    if (posTag.contains("VB")) {
      true
    } else {
      false
    }
  }

  // TODO(matt): this is potentially question-dependent, and for now I'm just going to make this a
  // major hack, hard-coding conversions from various question types that I know are in the babi
  // dataset.
  def convertQuestionAnswerToSentence(_question: String)(answer: String): String = {
    val question = _question.substring(0, _question.size - 1)
    val questionParts = question.split(" ")
    if (questionParts.size == 3 && question.startsWith("Where is ")) {
      questionParts.last + " is in the " + answer + "."
    } else if (questionParts.size == 4 && question.startsWith("Where is the ")) {
      "The " + questionParts.last + " is in the " + answer + "."
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
    val questionPosTags = parsedQuestion.getPosTags
    val questionWords = questionPosTags.map(_.word).toSet
    val lastOccurrences = new mutable.HashMap[String, String].withDefaultValue(null)

    // Now, first we add the dependency edges from the question sentence to the graph.
    parsedQuestion.getDependencies.foreach(dependency => {
      if (dependency.label != "root") {
        val headPosTag = questionPosTags(dependency.headIndex-1).posTag
        val depPosTag = questionPosTags(dependency.depIndex-1).posTag
        val keepHead = if (shouldLexicalizeWord(headPosTag)) "KEEP" else "REMOVE"
        val keepDep = if (shouldLexicalizeWord(depPosTag)) "KEEP" else "REMOVE"
        val headStr = s"Q${questionIndex}:${keepHead}:${dependency.head}"
        val depStr = s"Q${questionIndex}:${keepDep}:${dependency.dependent}"
        edges += Tuple3(headStr, dependency.label, depStr)
      }
    })

    // Now we add the dependency edges from all of the sentences in the history.
    history.zipWithIndex.foreach(sentence_idx => {
      val index = sentence_idx._2 + 1
      val sentence = sentence_idx._1
      val posTags = sentence.getPosTags
      sentence.getDependencies.foreach(dep => {
        if (dep.label != "root") {
          val headPosTag = posTags(dep.headIndex-1).posTag
          val depPosTag = posTags(dep.depIndex-1).posTag
          val keepHead = if (shouldLexicalizeWord(headPosTag)) "KEEP" else "REMOVE"
          val keepDep = if (shouldLexicalizeWord(depPosTag)) "KEEP" else "REMOVE"
          val headStr = s"${index}:${keepHead}:${dep.head}"
          val depStr = s"${index}:${keepDep}:${dep.dependent}"
          edges += Tuple3(headStr, dep.label, depStr)
        }
      })
      posTags.foreach(posTag => {
        val word = posTag.word
        if (posTag.posTag.contains("NN")) {
          val keepWord = if (shouldLexicalizeWord(posTag.posTag)) "KEEP" else "REMOVE"
          val wordStr = s"${keepWord}:${word}"
          if (questionWords.contains(word)) {
            edges += Tuple3(s"Q${questionIndex}:${wordStr}", "instance", s"${index}:${wordStr}")
          }
          if (lastOccurrences(word) != null) {
            edges += Tuple3(s"${index}:${wordStr}", "last instance", lastOccurrences(word))
          }
          lastOccurrences(word) = s"${index}:${wordStr}"
        }
      })
    })

    // Lastly, we add the remaining "last instance" edges.
    questionPosTags.foreach(posTag => {
      val word = posTag.word
      if (lastOccurrences(word) != null) {
        val keepWord = if (shouldLexicalizeWord(posTag.posTag)) "KEEP" else "REMOVE"
        val wordStr = s"${keepWord}:${word}"
        edges += Tuple3(s"Q${questionIndex}:${wordStr}", "last instance", lastOccurrences(word))
      }
    })

    edges.toSeq
  }
}
