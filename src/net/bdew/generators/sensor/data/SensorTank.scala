/*
 * Copyright (c) bdew, 2014 - 2017
 * https://github.com/bdew/generators
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.generators.sensor.data

import net.bdew.generators.sensor.{CastSensor, Icons}
import net.bdew.lib.data.DataSlotTankBase
import net.bdew.lib.sensors.GenericSensorParameter

import scala.reflect.ClassTag

case class SensorTank[T: ClassTag](uid: String, iconName: String, accessor: T => DataSlotTankBase) extends CastSensor[T] with Icons.Loader {
  override val parameters = Vector(
    ParameterFill.empty,
    ParameterFill.nonEmpty,
    ParameterFill.gt5,
    ParameterFill.gt25,
    ParameterFill.gt50,
    ParameterFill.gt75,
    ParameterFill.gt95,
    ParameterFill.nonFull,
    ParameterFill.full
  )
  override def getResultTyped(param: GenericSensorParameter, te: T) = (param, te) match {
    case (x: ParameterFill, y: T) =>
      val ds = accessor(y)
      x.test(ds.getFluidAmount, ds.getCapacity)
    case _ => false
  }
}
