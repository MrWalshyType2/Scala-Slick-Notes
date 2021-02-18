package object model extends VehicleHelper {

  val garage = List(CAR, HGV)

  def printInfo(vehicle: Vehicle): Unit = {
    println(s"VEHICLE OF TYPE: ${vehicle.typeOfVehicle}")
  }

  override def destroyVehicle(vehicle: Vehicle): Unit = println(s"DESTROYING VEHICLE OF TYPE: ${vehicle.typeOfVehicle}")
}
