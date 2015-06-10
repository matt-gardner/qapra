package org.allenai.qapra.babi

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

object set_up_experiments {
  val question1_training_file = "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_train.txt"
  val question1_testing_file = "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_test.txt"

  val split_base = "/home/mg1/qapra/splits/babi/"
  val question1_split_base = split_base + "question1/"

  val experiment_spec_base = "/home/mg1/qapra/experiment_specs/babi/"

  val relations = Seq("question1")

  val fileUtil = new FileUtil

  def makeSplitFiles() {
    val processor = new DataProcessor
    fileUtil.mkdirs(question1_split_base)
    processor.createSplit(question1_training_file, question1_split_base + "training.tsv")
    processor.createSplit(question1_testing_file, question1_split_base + "testing.tsv")

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
