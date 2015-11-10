package org.allenai.qapra.babi

import org.allenai.parse.ParsedSentence
import org.allenai.parse.StanfordParser
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import java.io.File

import scala.collection.JavaConverters._
import scala.collection.mutable

class DataProcessor(fileUtil: FileUtil = new FileUtil) {

  val parser = new StanfordParser

  // TODO(matt): these should probably be made into public fields in the Dataset object.
  val intraEdgeSeparator = "^,^"
  val interEdgeSeparator = " ### "

  def createSplit(question_file: String, out_file: String, isYesNo: Boolean) {
    val questions = extractQuestions(question_file)
    println(s"Found ${questions.size} questions, with ${questions.map(_._3.size).sum} sentences")
    val instances = questions.zipWithIndex.par.flatMap(question_idx => {
      val question = question_idx._1
      val index = question_idx._2 + 1
      val source = getSourceNodeFromQuestion(question._1, index)
      val parsedHistory = question._3.map(sentence => parser.parseSentence(sentence))
      val candidates = if (isYesNo) {
        Set(getCandidateFromYesNoQuestion(question._1))
      } else {
        val c = getCandidatesFromHistory(parsedHistory).toList
        if (question_file.contains("qa8")) {
          c ++ Set("nothing")
        } else {
          c
        }
      }
      val questionSentences = candidates.map(convertQuestionAnswerToSentence(question._1))
      val instanceGraphs = questionSentences.map(getGraphFromQuestion(parsedHistory, index))
      candidates.zip(instanceGraphs).map(candidateGraph => {
        val candidate = candidateGraph._1
        val graph = candidateGraph._2
        val isPositive = if (isYesNo) {
          question._2 == "yes"
        } else {
          candidate == question._2
        }
        // TODO(matt): kind of a hack.  I should return the POS tag with the candidate so this
        // isn't such a hack.
        val keep = if (shouldLexicalizeWord("NN")) "KEEP" else "REMOVE"
        val instance = (source, s"Q${index}:${keep}:${candidate.toLowerCase}", isPositive)
        (instance, graph)
      })
    }).seq
    println(s"Done processing instances, writing to ${out_file}")
    fileUtil.mkdirs(new File(out_file).getParent())
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

  def getSourceNodeFromQuestion(question: String, index: Int): String = {
    if (question.startsWith("What color is ")) {
      val np = question.substring(0, question.size - 1).split(" ").last
      s"Q${index}:REMOVE:${np.toLowerCase}"
    } else {
      val parsed = parser.parseSentence(question)
      val nouns = parsed.getPosTags.filter(_.posTag.contains("NN"))
      if (nouns.size == 0) {
        // HACK!  But Stanford fails to parse this sentence, probably because emily is lower case.
        if (question == "What is emily afraid of?") {
          s"Q${index}:REMOVE:emily"
        } else if (question.split(" ").size == 4 && question.startsWith("Where will ") &&
            question.endsWith(" go?")) {
          val np = question.split(" ")(2)
          s"Q${index}:REMOVE:${np.toLowerCase}"
        } else {
          println(s"Error finding noun in question: ${question}")
          s"Q${index}:REMOVE:ERROR"
        }
      } else {
        val keep = if (shouldLexicalizeWord(nouns(0).posTag)) "KEEP" else "REMOVE"
        s"Q${index}:${keep}:${nouns(0).word.toLowerCase}"
      }
    }
  }

  def getCandidatesFromHistory(history: Seq[ParsedSentence]): Set[String] = {
    val candidates = new mutable.HashSet[String]
    history.foreach(sentence => {
      sentence.getPosTags.map(taggedWord => {
        if (taggedWord.posTag.contains("NN") || taggedWord.posTag.contains("JJ")) {
          candidates += taggedWord.word
        }
      })
    })
    candidates.toSet
  }

  def getCandidateFromYesNoQuestion(_question: String): String = {
    val question = _question.substring(0, _question.size - 1)
    val questionParts = question.split(" ")
    if (matchesQuestionType6(question, questionParts)) {
      questionParts.last
    } else if (matchesQuestionType17(question, questionParts)) {
      questionParts.last
    } else if (matchesQuestionType18a(question, questionParts)
        || matchesQuestionType18b(question, questionParts)) {
      val np = questionParts.last
      if (np == "chocolates") {
        "box"
      } else {
        np
      }
    } else {
      println("Get candidate failed on question: " + question)
      throw new RuntimeException("getCandidateFromYesNoQuestion not finished yet!")
    }
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
    if (matchesQuestionType1(question, questionParts)) {
      questionParts.last + " is in the " + answer + "."
    } else if (matchesQuestionType2(question, questionParts)) {
      "The " + questionParts.last + " is in the " + answer + "."
    } else if (matchesQuestionType3(question, questionParts)) {
      "The " + questionParts(3) + " was in the " + answer + " before the " + questionParts.last + "."
    } else if (matchesQuestionType4a(question, questionParts)) {
      "The " + answer + " is " + questionParts(2) + " of the " + questionParts.last + "."
    } else if (matchesQuestionType4b(question, questionParts)) {
      "The " + questionParts(3) + " is " + questionParts(4) + " of the " + answer + "."
    } else if (matchesQuestionType5a(question, questionParts)) {
      questionParts(2) + " gave " + questionParts(5) + " the " + answer + "."
    } else if (matchesQuestionType5b(question, questionParts)) {
      answer + " received the " + questionParts(3)
    } else if (matchesQuestionType5c(question, questionParts)) {
      questionParts(2) + " gave the " + questionParts(5) + " to " + answer + "."
    } else if (matchesQuestionType5d(question, questionParts)) {
      answer + " gave the " + questionParts(3) + " to " + questionParts(5) + "."
    } else if (matchesQuestionType5e(question, questionParts)) {
      answer + " gave the " + questionParts(3) + "."
    } else if (matchesQuestionType6(question, questionParts)) {
      questionParts(1) + " is in the " + questionParts.last + "."
    } else if (matchesQuestionType8(question, questionParts)) {
      questionParts(2) + " is carrying the " + answer + "."
    } else if (matchesQuestionType14(question, questionParts)) {
      questionParts(2) + " was in the " + answer + " before the " + questionParts.last + "."
    } else if (matchesQuestionType15(question, questionParts)) {
      questionParts(2) + " is afraid of " + answer + "."
    } else if (matchesQuestionType16(question, questionParts)) {
      questionParts(3) + " is " + answer + "."
    } else if (matchesQuestionType17(question, questionParts)) {
      val firstShape = shapes.intersect(questionParts.toSet).map(s => questionParts.indexOf(s)).min
      val shape = if (questionParts(firstShape - 1) == "the") {
        questionParts(firstShape)
      } else {
        questionParts(firstShape - 1) + " " + questionParts(firstShape)
      }
      "The " + shape + " is " + questionParts.drop(firstShape + 1).mkString(" ") + "."
    } else if (matchesQuestionType18a(question, questionParts)) {
      val biggerIndex = questionParts.indexOf("bigger")
      if (biggerIndex == 3) {
        "The " + questionParts(2) + " is " + questionParts.drop(3).mkString(" ") + "."
      } else if (biggerIndex == 5) {
        val np = questionParts(2) + " " + questionParts(3) + " " + questionParts(4)
        "The " + np + " is " + questionParts.drop(5).mkString(" ") + "."
      } else {
        throw new RuntimeException("Unexpected question format")
      }
      questionParts(2) + " will go to the " + answer + "."
    } else if (matchesQuestionType18b(question, questionParts)) {
      val fitIndex = questionParts.indexOf("fit")
      if (fitIndex == 3) {
        "The " + questionParts(2) + " fits in " + questionParts.drop(5).mkString(" ") + "."
      } else if (fitIndex == 5) {
        val np = questionParts(2) + " " + questionParts(3) + " " + questionParts(4)
        "The " + np + " fits in " + questionParts.drop(7).mkString(" ") + "."
      } else {
        throw new RuntimeException("Unexpected question format")
      }
      questionParts(2) + " will go to the " + answer + "."
    } else if (matchesQuestionType20a(question, questionParts)) {
      questionParts(2) + " will go to the " + answer + "."
    } else if (matchesQuestionType20b(question, questionParts)) {
      questionParts(2) + " went to the " + questionParts(6) + " because he was " + answer + "."
    } else if (matchesQuestionType20c(question, questionParts)) {
      questionParts(2) + " got the " + questionParts(5) + " because he was " + answer + "."
    } else {
      println("Failed on question: " + question)
      throw new RuntimeException("This hackish method not finished yet!")
    }
  }

  def matchesQuestionType1(question: String, questionParts: Array[String]) = {
    questionParts.size == 3 && question.startsWith("Where is ")
  }

  def matchesQuestionType2(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("Where is the ")
  }

  def matchesQuestionType3(question: String, questionParts: Array[String]) = {
    questionParts.size == 7 && question.startsWith("Where was the ") && question.contains("before the")
  }

  val directions = Set("north", "south", "east", "west")
  def matchesQuestionType4a(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("What is ") &&
      directions.contains(questionParts(2)) && question.contains("of the")
  }

  def matchesQuestionType4b(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("What is the ") &&
      directions.contains(questionParts(4)) && questionParts.last == "of"
  }

  def matchesQuestionType5a(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("What did ") &&
      question.contains("give to")
  }

  def matchesQuestionType5b(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("Who received the ")
  }

  def matchesQuestionType5c(question: String, questionParts: Array[String]) = {
    questionParts.size == 7 && question.startsWith("Who did ") && question.contains("give the") &&
      question.endsWith(" to")
  }

  def matchesQuestionType5d(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("Who gave the ") && question.contains(" to ")
  }

  def matchesQuestionType5e(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("Who gave the ")
  }

  def matchesQuestionType6(question: String, questionParts: Array[String]) = {
    questionParts.size == 5 && question.startsWith("Is ") && question.contains(" in the ")
  }

  def matchesQuestionType8(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("What is ") && question.endsWith(" carrying")
  }

  def matchesQuestionType14(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("Where was ") && question.contains("before the")
  }

  def matchesQuestionType15(question: String, questionParts: Array[String]) = {
    questionParts.size == 5 && question.startsWith("What is ") && question.contains(" afraid of")
  }

  def matchesQuestionType16(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("What color is ")
  }

  val shapes = Set("square", "sphere", "rectangle", "triangle")
  def matchesQuestionType17(question: String, questionParts: Array[String]) = {
    shapes.intersect(questionParts.toSet).size > 0 && question.startsWith("Is the ")
  }

  def matchesQuestionType18a(question: String, questionParts: Array[String]) = {
    question.startsWith("Is the ") && question.contains(" bigger than the ")
  }

  def matchesQuestionType18b(question: String, questionParts: Array[String]) = {
    question.startsWith("Does the ") && question.contains(" fit in the ")
  }

  def matchesQuestionType20a(question: String, questionParts: Array[String]) = {
    questionParts.size == 4 && question.startsWith("Where will ") && question.endsWith(" go")
  }

  def matchesQuestionType20b(question: String, questionParts: Array[String]) = {
    questionParts.size == 7 && question.startsWith("Why did ") && question.contains(" go to the ")
  }

  def matchesQuestionType20c(question: String, questionParts: Array[String]) = {
    questionParts.size == 6 && question.startsWith("Why did ") && question.contains(" get the ")
  }

  type Edge = (String, String, String)
  // TODO(matt): This doesn't appear to be a big issue on the babi data, but I really need to index
  // the words in the sentence, not just the sentences.  All occurrences of "the" in a sentence
  // will currently get the same node.
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
        val headStr = s"Q${questionIndex}:${keepHead}:${dependency.head.toLowerCase}"
        val depStr = s"Q${questionIndex}:${keepDep}:${dependency.dependent.toLowerCase}"
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
          val headStr = s"${index}:${keepHead}:${dep.head.toLowerCase}"
          val depStr = s"${index}:${keepDep}:${dep.dependent.toLowerCase}"
          edges += Tuple3(headStr, dep.label, depStr)
        }
      })
      posTags.foreach(posTag => {
        val word = posTag.word.toLowerCase
        if (posTag.posTag.contains("NN") || posTag.posTag.contains("JJ")) {
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
      val word = posTag.word.toLowerCase
      if (lastOccurrences(word) != null) {
        val keepWord = if (shouldLexicalizeWord(posTag.posTag)) "KEEP" else "REMOVE"
        val wordStr = s"${keepWord}:${word}"
        edges += Tuple3(s"Q${questionIndex}:${wordStr}", "last instance", lastOccurrences(word))
      }
    })

    edges.toSeq
  }
}
