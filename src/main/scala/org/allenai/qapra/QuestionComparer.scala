package org.allenai.qapra

import java.io.File

object QuestionComparer {
  val user_home = System.getProperty("user.home")
  val simplified_question_triple_file = s"${user_home}/ai2/misc/simplified_question_triples.txt"
  val results_dir = s"${user_home}/ai2/pra/results/"


  def main(args: Array[String]) {
    val experiment1 = args(0)
    val experiment2 = args(1)
    println(s"Comparing experiments $experiment1 and $experiment2")
    val dir1 = new File(results_dir + experiment1 + "/")
    val dir2 = new File(results_dir + experiment2 + "/")
    val results1 = QuestionHelper.scoreQuestions(simplified_question_triple_file, dir1)
    val results2 = QuestionHelper.scoreQuestions(simplified_question_triple_file, dir2)
    val questions = results1.questions
    val answers = (results1.answers zip results2.answers).zipWithIndex
    for ((answer, index) <- answers) {
      println(s"Question ${index+1}: ${questions(index).question_text}")
      if (answer._1.answer == answer._2.answer) {
        if (answer._1.correct) {
          println(s"Both systems had the same correct answer: ${answer._1.answer}")
        } else {
          println(s"Both systems had the same wrong answer: ${answer._1.answer}")
        }
      } else {
        println(s"$experiment1's answer was ${answer._1.answer} (${answer._1.correct})")
        println(s"$experiment2's answer was ${answer._2.answer} (${answer._2.correct})")
      }
      val scores = answer._1.choice_scores.flatMap(_.triple_scores) zip answer._2.choice_scores.flatMap(_.triple_scores)
      println("Triple scores:")
      for (triple_score <- scores) {
        println(f"  ${triple_score._1._1}: (${triple_score._1._2}%4.2f, ${triple_score._2._2}%4.2f)")
      }
      println()
    }
  }

}
