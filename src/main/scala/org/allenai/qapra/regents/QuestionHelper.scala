package org.allenai.qapra.regents

import java.io.File

import scala.collection.mutable
import scalax.io.Resource
import scalax.io.LongTraversable

import edu.cmu.ml.rtw.users.matt.util.{FileHelper => PraFileHelper}

case class Choice(name: String, triples: List[TripleWithLabel], correct: Boolean)
case class Question(question_text: String, choices: List[Choice])
case class ChoiceScore(triple_scores: List[TripleWithScore])
case class Answer(answer: String, correct: Boolean, choice_scores: List[ChoiceScore])

// This class does operations related to Aristo questions - reading the questions from a file,
// scoring answers to questions, and so forth.  Because it's related to scoring questions, we also
// include code that will read in a PRA scores.tsv file here.
object QuestionHelper {

  def readScores(results_dir: File) = {
    val scores_files = PraFileHelper.recursiveListFiles(results_dir, """.*scores.tsv$""".r)
    (scores_files.flatMap(getTripleScoresFromFile).toMap, scores_files.size)
  }

  def getTripleScoresFromFile(file: File) = {
    val relation = file.getParent().split("/").last
    val triples = mutable.ListBuffer[TripleWithScore]()
    for (line <- Resource.fromFile(file).lines()) {
      val fields = line.split("\t")
      if (fields.length > 2 && fields(1).nonEmpty) {
        val arg1 = fields(0)
        val arg2 = fields(1)
        val score = fields(2).toDouble
        triples += Tuple2((arg1, relation, arg2), score)
      }
    }
    triples.toList
  }

  def readQuestions(question_file: String) = {
    readQuestionsFromLines(Resource.fromClasspath(question_file).lines())
  }

  def readQuestionsFromLines(lines: LongTraversable[String]) = {
    val questions = mutable.ListBuffer[Question]()
    var question_text: String = null
    var choices: mutable.ListBuffer[Choice] = null
    var choice_name: String = null
    var correct_choice: Boolean = false
    var triples: mutable.ListBuffer[TripleWithLabel] = null
    for (line <- lines) {
      line match {
        case newline if newline.isEmpty() => {
          if (question_text == null || choices == null) {
            println("Error reading question file!")
            System.exit(-1)
          }
          choices += Choice(choice_name, triples.toList, correct_choice)
          questions += Question(question_text, choices.toList)
          question_text = null
          choices = null
          choice_name = null
          triples = null
        }
        case choice if choice.substring(0, 2).contains(":") => {
          if (choice_name != null) {
            choices += Choice(choice_name, triples.toList, correct_choice)
          }
          choice_name = choice.split(":")(0)
          correct_choice = choice.contains("+")
          triples = new mutable.ListBuffer[TripleWithLabel]
        }
        case triple if triple.substring(0, 2).contains("(") => {
          triples += readTripleFromQuestionFile(triple)
        }
        case question_line => {
          question_text = question_line
          choices = new mutable.ListBuffer[Choice]
        }
      }
    }
    questions.toList
  }

  def readTripleFromQuestionFile(line: String): TripleWithLabel = {
    val correct = line.charAt(0) != '*'
    val pieces = line.split(",")
    val arg1 = pieces(0).split('(').last.trim.toLowerCase
    val relation = pieces(1).trim.toLowerCase
    val arg2 = pieces(2).split(')')(0).trim.toLowerCase
    ((arg1, relation, arg2), correct)
  }

  def scoreQuestions(question_triple_file: String, results_dir: File) = {
    val questions = readQuestions(question_triple_file)
    val scores_tuple = readScores(results_dir)
    val scores = scores_tuple._1.withDefaultValue(0.0)
    val num_score_files = scores_tuple._2
    var num_correct = 0
    var num_unanswered = 0
    val answers = new mutable.ListBuffer[Answer]
    for (question <- questions) {
      var max_score = 0.0
      var best_choice: Choice = null
      val choice_scores = new mutable.ListBuffer[ChoiceScore]
      for (choice <- question.choices) {
        var score = 0.0
        val triple_scores = new mutable.ListBuffer[TripleWithScore]
        for (triple <- choice.triples) {
          val triple_score = scores(triple._1)
          score += triple_score
          triple_scores += Tuple2(triple._1, triple_score)
        }
        choice_scores += ChoiceScore(triple_scores.toList)
        if (score > max_score) {
          max_score = score
          best_choice = choice
        }
      }
      if (best_choice == null) {
        answers += Answer("", false, choice_scores.toList)
      } else {
        answers += Answer(best_choice.name, best_choice.correct, choice_scores.toList)
      }
    }
    Results(questions, answers.toList)
  }
}

case class Results(questions: List[Question], answers: List[Answer])
