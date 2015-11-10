package org.allenai.qapra.babi

import edu.cmu.ml.rtw.users.matt.util.FileUtil

import scala.collection.JavaConverters._

object set_up_experiments {
  val training_files = Map(
    1 -> "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_train.txt",
    2 -> "/home/mg1/data/babi/tasksv11/en/qa2_two-supporting-facts_train.txt",
    3 -> "/home/mg1/data/babi/tasksv11/en/qa3_three-supporting-facts_train.txt",
    4 -> "/home/mg1/data/babi/tasksv11/en/qa4_two-arg-relations_train.txt",
    5 -> "/home/mg1/data/babi/tasksv11/en/qa5_three-arg-relations_train.txt",
    6 -> "/home/mg1/data/babi/tasksv11/en/qa6_yes-no-questions_train.txt",
    7 -> "/home/mg1/data/babi/tasksv11/en/qa7_counting_train.txt",
    8 -> "/home/mg1/data/babi/tasksv11/en/qa8_lists-sets_train.txt",
    9 -> "/home/mg1/data/babi/tasksv11/en/qa9_simple-negation_train.txt",
    10 -> "/home/mg1/data/babi/tasksv11/en/qa10_indefinite-knowledge_train.txt",
    11 -> "/home/mg1/data/babi/tasksv11/en/qa11_basic-coreference_train.txt",
    12 -> "/home/mg1/data/babi/tasksv11/en/qa12_conjunction_train.txt",
    13 -> "/home/mg1/data/babi/tasksv11/en/qa13_compound-coreference_train.txt",
    14 -> "/home/mg1/data/babi/tasksv11/en/qa14_time-reasoning_train.txt",
    15 -> "/home/mg1/data/babi/tasksv11/en/qa15_basic-deduction_train.txt",
    16 -> "/home/mg1/data/babi/tasksv11/en/qa16_basic-induction_train.txt",
    17 -> "/home/mg1/data/babi/tasksv11/en/qa17_positional-reasoning_train.txt",
    18 -> "/home/mg1/data/babi/tasksv11/en/qa18_size-reasoning_train.txt",
    19 -> "/home/mg1/data/babi/tasksv11/en/qa19_path-finding_train.txt",
    20 -> "/home/mg1/data/babi/tasksv11/en/qa20_agents-motivations_train.txt"
  )

  val testing_files = Map(
    1 -> "/home/mg1/data/babi/tasksv11/en/qa1_single-supporting-fact_test.txt",
    2 -> "/home/mg1/data/babi/tasksv11/en/qa2_two-supporting-facts_test.txt",
    3 -> "/home/mg1/data/babi/tasksv11/en/qa3_three-supporting-facts_test.txt",
    4 -> "/home/mg1/data/babi/tasksv11/en/qa4_two-arg-relations_test.txt",
    5 -> "/home/mg1/data/babi/tasksv11/en/qa5_three-arg-relations_test.txt",
    6 -> "/home/mg1/data/babi/tasksv11/en/qa6_yes-no-questions_test.txt",
    7 -> "/home/mg1/data/babi/tasksv11/en/qa7_counting_test.txt",
    8 -> "/home/mg1/data/babi/tasksv11/en/qa8_lists-sets_test.txt",
    9 -> "/home/mg1/data/babi/tasksv11/en/qa9_simple-negation_test.txt",
    10 -> "/home/mg1/data/babi/tasksv11/en/qa10_indefinite-knowledge_test.txt",
    11 -> "/home/mg1/data/babi/tasksv11/en/qa11_basic-coreference_test.txt",
    12 -> "/home/mg1/data/babi/tasksv11/en/qa12_conjunction_test.txt",
    13 -> "/home/mg1/data/babi/tasksv11/en/qa13_compound-coreference_test.txt",
    14 -> "/home/mg1/data/babi/tasksv11/en/qa14_time-reasoning_test.txt",
    15 -> "/home/mg1/data/babi/tasksv11/en/qa15_basic-deduction_test.txt",
    16 -> "/home/mg1/data/babi/tasksv11/en/qa16_basic-induction_test.txt",
    17 -> "/home/mg1/data/babi/tasksv11/en/qa17_positional-reasoning_test.txt",
    18 -> "/home/mg1/data/babi/tasksv11/en/qa18_size-reasoning_test.txt",
    19 -> "/home/mg1/data/babi/tasksv11/en/qa19_path-finding_test.txt",
    20 -> "/home/mg1/data/babi/tasksv11/en/qa20_agents-motivations_test.txt"
  )

  val split_base = "/home/mg1/qapra/splits/babi/"

  val experiment_spec_base = "/home/mg1/qapra/experiment_specs/babi/"

  val yesNoQuestionTypes = Set(6, 9, 10, 17, 18)

  val questionTypes = Seq(
    1, // done
    2, // done
    3, // done
    4, // done
    5, // done
    6, //yes/no questions that need special treatment
    //7, counting questions that need special treatment
    8, // done, but needs more work to handle lists of things, but we can still answer some correctly
    9, //yes/no questions that need special treatment
    10, //yes/no/maybe questions that need special treatment
    11, // done, but needs coref
    12, // done
    13, // done, but needs coref
    14, // done
    15, // done, but needs lemmatization
    16, // done
    17, //yes/no questions that need special treatment
    18, //yes/no questions that need special treatment
    //19, path questions that need special treatment
    20  // done
    )

  val fileUtil = new FileUtil
  val processor = new DataProcessor

  def makeSplitFiles() {
    for (questionType <- questionTypes) {
      println(s"Create split files for question type ${questionType}")
      val base = split_base + f"question${questionType}%02d/"
      val isYesNo = yesNoQuestionTypes.contains(questionType)
      processor.createSplit(training_files(questionType), base + "training.tsv", isYesNo)
      processor.createSplit(testing_files(questionType), base + "testing.tsv", isYesNo)
    }
    val relations = questionTypes.map(i => f"question${i}%02d")
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
