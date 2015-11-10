package org.allenai.qapra.babi

import edu.cmu.ml.rtw.pra.experiments.BasicMetricComputer
import edu.cmu.ml.rtw.pra.experiments.MetricComputer
import edu.cmu.ml.rtw.pra.experiments.RelationMetrics
import edu.cmu.ml.rtw.pra.experiments.EmptyMetricsWithDefault
import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._
import scala.collection.mutable

object ExperimentScorer {
  val user_home = System.getProperty("user.home")
  val pra_base_ = s"${user_home}/qapra"

  val sortResultsBy = List("-ACC")
  val displayMetrics = List(
    ("ACC", "ACC"),
    ("MAP", "MAP"),
    ("MRR", "MRR")
  )
  val metricComputers = List(
    BasicMetricComputer,
    AccuracyComputer
  )

  val relationMetrics = Seq("AP", "Accuracy")
  val significanceTests = Seq[String]()

  def main(args: Array[String]) {
    val pra_base = if (args.length > 0) args(0) else pra_base_
    val filters = args.toList.drop(1)
    edu.cmu.ml.rtw.pra.experiments.ExperimentScorer.scoreExperiments(pra_base,
      filters,
      displayMetrics,
      sortResultsBy,
      metricComputers,
      significanceTests,
      relationMetrics)
  }
}

object AccuracyComputer extends MetricComputer {

  val fileUtil = new FileUtil

  def questionTypeIsYesNo(filename: String): Boolean = {
    if (filename.contains("question06")) {
      true
    } else if (filename.contains("question09")) {
      true
    } else if (filename.contains("question10")) {
      true
    } else if (filename.contains("question17")) {
      true
    } else if (filename.contains("question18")) {
      true
    } else {
      false
    }
  }

  def readQuestionAnswersFromFile(filename: String) = {
    val questions = new mutable.ListBuffer[(String, Seq[(String, Double, Boolean)])]
    val lines = fileUtil.readLinesFromFile(filename).asScala

    val isYesNo = questionTypeIsYesNo(filename)
    var currentQuestion = ""
    var currentAnswers = new mutable.ListBuffer[(String, Double, Boolean)]
    for (line <- lines) {
      val parts = line.split("\t")
      if (parts.size >= 3) {
        currentQuestion = parts(0)
        val target = parts(1)
        val score = parts(2).toDouble
        val correct = if (isYesNo) {
          val answerIsYes = parts.size > 3 && parts(3) == "*"
          val predictedYes = score > 0
          answerIsYes == predictedYes
        } else {
          parts.size > 3 && parts(3) == "*"
        }
        currentAnswers += Tuple3(target, score, correct)
      } else {
        questions += Tuple2(currentQuestion, currentAnswers.toSeq)
        currentAnswers = new mutable.ListBuffer[(String, Double, Boolean)]
        currentQuestion = ""
      }
    }
    questions.toSeq
  }

  def computeDatasetMetrics(results_dir: String, split_dir: String, relation_metrics: RelationMetrics) = {
    val metrics = EmptyMetricsWithDefault
    metrics("ACC") = relation_metrics.map(_._2("Accuracy")).sum / relation_metrics.keys.size
    metrics
  }

  def computeRelationMetrics(results_file: String, test_split_file: String) = {
    val metrics = EmptyMetricsWithDefault

    val questionAnswers = readQuestionAnswersFromFile(results_file)
    val questionCorrect = questionAnswers.map(qA => {
      if (qA._2(0)._3)
        true
      else
        false
    })
    val numCorrect = questionCorrect.filter(correct => correct).size
    val accuracy = numCorrect.toDouble / questionCorrect.size

    metrics("Accuracy") = accuracy
    metrics
  }

  def datasetMetricsComputed = Seq()

  def relationMetricsComputed = Seq("Accuracy")
}
