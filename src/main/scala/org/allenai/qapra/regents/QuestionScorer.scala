package org.allenai.qapra.regents

import edu.cmu.ml.rtw.pra.experiments.BasicMetricComputer
import edu.cmu.ml.rtw.pra.experiments.ExperimentScorer
import edu.cmu.ml.rtw.pra.experiments.MetricComputer
import edu.cmu.ml.rtw.pra.experiments.RelationMetrics
import edu.cmu.ml.rtw.pra.experiments.EmptyMetricsWithDefault

import java.io.File

object QuestionScorer {
  val user_home = System.getProperty("user.home")
  val pra_base_ = s"${user_home}/ai2/pra/"

  val sortResultsBy = List("-num_correct", "-num_unanswered")
  val displayMetrics = List(
    ("num_correct", "Correct"),
    ("num_unanswered", "Unanswered"),
    ("precision", "Precision"),
    ("recall", "Recall"),
    ("aristo", "Aristo Accuracy"),
    ("MAP", "MAP"),
    ("MRR", "MRR")
  )
  val metricComputers = List(
    BasicMetricComputer,
    QuestionMetricComputer
  )

  def main(args: Array[String]) {
    val pra_base = if (args.length > 0) args(0) else pra_base_
    val filters = args.toList.drop(1)
    ExperimentScorer.scoreExperiments(pra_base,
      filters,
      displayMetrics,
      sortResultsBy,
      metricComputers,
      Seq[String](),
      Seq[String]())
  }
}

object QuestionMetricComputer extends MetricComputer {
  val simplified_question_triple_file = "simplified_question_triples.txt"

  def computeDatasetMetrics(results_dir: String, split_dir: String, relation_metrics: RelationMetrics) = {
    val results = QuestionHelper.scoreQuestions(simplified_question_triple_file, new File(results_dir))
    val metrics = EmptyMetricsWithDefault
    metrics("num_correct") = results.answers.map(_.correct).map(if (_) 1 else 0).sum
    metrics("num_unanswered") = results.answers.map(_.answer.length == 0).map(if (_) 1 else 0).sum
    val num_questions = results.questions.length
    metrics("precision") = metrics("num_correct") / (num_questions - metrics("num_unanswered"))
    metrics("recall") = metrics("num_correct") / num_questions
    metrics("aristo") = (metrics("num_correct") + metrics("num_unanswered") * .25) / num_questions
    metrics
  }

  def computeRelationMetrics(results_file: String, test_split_file: String) = EmptyMetricsWithDefault

  def datasetMetricsComputed = {
    Seq(
      "num_correct",
      "num_unanswered",
      "precision",
      "recall",
      "aristo"
    )
  }

  def relationMetricsComputed = { Seq() }
}
