package org.allenai.qapra.babi

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

object set_up_experiments {
  val question1_training_file = "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_train.txt"
  val question1_testing_file = "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_test.txt"

  val question2_training_file = "/home/mg1/data/babi/tasksv11/en/qa2_two-supporting-facts_train.txt"
  val question2_testing_file = "/home/mg1/data/babi/tasksv11/en/qa2_two-supporting-facts_test.txt"

  val split_base = "/home/mg1/qapra/splits/babi/"
  val question1_split_base = split_base + "question1/"
  val question2_split_base = split_base + "question2/"

  val experiment_spec_base = "/home/mg1/qapra/experiment_specs/babi/"

  val relations = Seq("question1", "question2")

  val fileUtil = new FileUtil
  val processor = new DataProcessor

  def processQuestion1() {
    fileUtil.mkdirs(question1_split_base)
    processor.createSplit(question1_training_file, question1_split_base + "training.tsv")
    processor.createSplit(question1_testing_file, question1_split_base + "testing.tsv")
  }

  def processQuestion2() {
    fileUtil.mkdirs(question2_split_base)
    processor.createSplit(question2_training_file, question2_split_base + "training.tsv")
    processor.createSplit(question2_testing_file, question2_split_base + "testing.tsv")
  }

  def makeSplitFiles() {
    //processQuestion1()
    processQuestion2()
    fileUtil.writeLinesToFile(split_base + "relations_to_run.tsv", relations.asJava)
  }

  def makeExperimentSpecFiles() {
    fileUtil.mkdirs(experiment_spec_base)

    // The last two here are necessary for now because SpecFileReader doesn't like it if all you
    // have are load statements.
    val spec_lines = Seq("load default_babi_parameters", "{", "}")
    fileUtil.writeLinesToFile(experiment_spec_base + "sfe.json", spec_lines.asJava)
  }

  def main(args: Array[String]) {
    makeSplitFiles()
    makeExperimentSpecFiles()
  }
}
