package model

case class Vehicle(typeOfVehicle: String) {
  def someMethod(): Unit = println("Hello")
}

object Motorbike extends Vehicle("Motorbike")
object HGV extends Vehicle("HGV")
object LGV extends Vehicle("LGV")
object CAR extends Vehicle("CAR")

