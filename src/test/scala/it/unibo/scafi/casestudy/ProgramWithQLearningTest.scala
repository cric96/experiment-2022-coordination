package it.unibo.scafi.casestudy

import it.unibo.AlchemistHelper._
import it.unibo.alchemist.model.implementations.times.DoubleTime
import it.unibo.alchemist.model.interfaces.{GeoPosition, Time}
import it.unibo.learning.{Episode, Q}
import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should
import org.scalatestplus.junit.JUnitRunner
import os.Path

@RunWith(classOf[JUnitRunner])
@SuppressWarnings(Array("org.wartremover.warts.Any")) // because of alchemist molecule management
class ProgramWithQLearningTest extends AnyFlatSpec with should.Matchers {
  val path: Path = os.pwd / "src" / "test" / "yaml" / "programCheck.yml"

  "HopCountQLearning" should "update q tables when learn" in {
    val engine = loadAlchemist[Any, GeoPosition](path)
    engine.forEach(_.put("learn", true))
    engine.play()
    engine.run()
    engine.forEach { node =>
      assert(node.get[Q[Int, Int]]("q") != Q.zeros[Int, Int]())
      val output = node.get[Double]("output")
      assert(output == 10 || output == 0)
    }
  }

  "HopCountQLearning" should "not update q tables when act" in {
    val engine = loadAlchemist[Any, GeoPosition](path)
    engine.forEach(_.put("learn", false))
    engine.play()
    engine.run()
    engine.forEach { node =>
      assert(node.get[Q[Int, Int]]("q") == Q.zeros[Int, Int]())
    }
  }
}
