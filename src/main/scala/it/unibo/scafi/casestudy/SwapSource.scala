package it.unibo.scafi.casestudy

import cats.data.NonEmptySet
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.learning.{Clock, Q, QLearning, TimeVariable}
import it.unibo.storage.LocalStorage

import scala.util.Random

class SwapSource
    extends AggregateProgram
    with HopCountQLearning
    with StandardSensors
    with ScafiAlchemistSupport
    with Gradients {
  // Implicit context variable
  implicit lazy val rand: Random = randomGen
  // Storage
  lazy val qTableStorage = new LocalStorage[Int]("qtables")
  lazy val clockTableStorage = new LocalStorage[Int]("clock")
  lazy val passedTime: Double = alchemistTimestamp.toDouble
  // Variable loaded by alchemist configuration.
  lazy val leftSrc: Int = node.get[Integer]("left_source") // ID of the source at the left of the env (the stable one)
  lazy val rightSrc: Int =
    node.get[Integer]("right_source") // ID of the source at the right of the env (the unstable one)
  lazy val rightSrcStop: Int =
    node.get[Integer]("stop_right_source") // time at which the source at the right of the env stops being a source
  lazy val learnCondition: Boolean = node.get[java.lang.Boolean]("learn")
  lazy val initialValue: Double = node.get[Double]("initial_value")
  // Other constant
  lazy val windowDifferenceSize: Int = 3
  lazy val trajectorySize: Int = 7
  // Learning constants
  lazy val alpha: TimeVariable[Double] =
    TimeVariable.independent(0.1) // TODO this should be put in the alchemist configuration
  lazy val epsilon: TimeVariable[Double] =
    TimeVariable.independent(0.1) // TODO this should be put in the alchemist configuration
  lazy val gamma: Double = node.get[java.lang.Double]("gamma")
  // Q Learning data
  lazy val actions: NonEmptySet[Int] = NonEmptySet.of(0, 1) // TODO this should be put int the alchemist configuration
  // Pickle loose the default, so we need to replace it each time the map is loaded
  lazy val q: Q[List[Int], Int] = qTableStorage.loadOrElse(mid(), Q.zeros[List[Int], Int]()).withDefault(initialValue)
  lazy val qLearning: QLearning[List[Int], Int] = QLearning(actions, alpha, gamma)
  // Source condition
  override def source: Boolean =
    if (mid() == leftSrc || (mid() == rightSrc && passedTime < rightSrcStop)) true else false
  // Aggregate Program data
  lazy val hopCountMetric: Metric = () => 1
  lazy val clock: Clock = clockTableStorage.loadOrElse(mid(), Clock.start)
  // Aggregate program
  override def main(): Any = {
    val classicHopCount = classicGradient(source, hopCountMetric) // BASELINE
    val hopCountWithoutRightSource =
      classicGradient(mid() == leftSrc, hopCountMetric) // optimal gradient when RIGHT_SRC stops being a source
    val refHopCount = if (passedTime >= rightSrcStop) hopCountWithoutRightSource else classicHopCount
    // Learning definition
    val learningProblem = learningProcess(q)
      .stateDefinition(stateFromWindow)
      .rewardDefinition(output => rewardSignal(refHopCount.toInt, output))
      .actionEffectDefinition((output, action) => output + action)
      .initialConditionDefinition(List.empty, Double.PositiveInfinity)
    // RL Program execution
    val roundData = branch(learnCondition) {
      learningProblem.learn(qLearning, epsilon, Clock.start)
    } {
      learningProblem.act(qLearning, Clock.start)
    }
    // Store alchemist info
    node.put[Q[List[Int], Int]]("qtable", roundData.q)
    // Store update data
    qTableStorage.save(mid(), roundData.q)
    clockTableStorage.save(mid(), roundData.clock)
  }

  private def stateFromWindow(output: Double): List[Int] = List.empty

  private def rewardSignal(groundTruth: Double, currentValue: Double): Double =
    if ((groundTruth - currentValue) == 0) { 0 }
    else { -1 }

}
