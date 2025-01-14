/*
 * Copyright (c) bdew, 2014 - 2017
 * https://github.com/bdew/generators
 *
 * This mod is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://bdew.net/minecraft-mod-public-license/
 */

package net.bdew.generators.controllers.turbine

import net.bdew.generators.config.{Modules, TurbineFuel}
import net.bdew.generators.control.{CIControl, ControlActions}
import net.bdew.generators.controllers.PoweredController
import net.bdew.generators.modules.efficiency.{BlockEfficiencyUpgradeTier1, BlockEfficiencyUpgradeTier2}
import net.bdew.generators.modules.powerCapacitor.BlockPowerCapacitor
import net.bdew.generators.modules.turbine.BlockTurbine
import net.bdew.generators.sensor.Sensors
import net.bdew.generators.{Generators, GeneratorsResourceProvider}
import net.bdew.lib.Misc
import net.bdew.lib.PimpVanilla._
import net.bdew.lib.data._
import net.bdew.lib.data.base.UpdateKind
import net.bdew.lib.multiblock.interact.{CIFluidInput, CIOutputFaces, CIPowerProducer}
import net.bdew.lib.multiblock.tile.{TileControllerGui, TileModule}
import net.bdew.lib.power.DataSlotPower
import net.bdew.lib.sensors.multiblock.CIRedstoneSensors
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler

class TileTurbineController extends TileControllerGui with PoweredController with CIFluidInput with CIOutputFaces with CIPowerProducer with CIRedstoneSensors with CIControl {
  val cfg = MachineTurbine

  val resources = GeneratorsResourceProvider

  val fuel = new DataSlotTank("fuel", this, 0, canDrainExternal = false) {
    override def canFillFluidType(fluid: FluidStack): Boolean = TurbineFuel.isValidFuel(fluid)
  }

  val power = new DataSlotPower("power", this)

  val maxMJPerTick = new DataSlotFloat("maxMJPerTick", this).setUpdate(UpdateKind.SAVE, UpdateKind.GUI)
  val burnTime = new DataSlotFloat("burnTime", this).setUpdate(UpdateKind.SAVE)

  val numTurbines = new DataSlotInt("turbines", this).setUpdate(UpdateKind.GUI)
  val fuelPerTick = new DataSlotFloat("fuelPerTick", this).setUpdate(UpdateKind.GUI)

  val fuelEfficiency = new DataSlotFloat("fuelConsumptionMultiplier", this).setUpdate(UpdateKind.SAVE, UpdateKind.GUI)

  val outputAverage = new DataSlotMovingAverage("outputAverage", this, 20)
  val fuelPerTickAverage = new DataSlotMovingAverage("fuelPerTickAverage", this, 20)

  override val redstoneSensorsType = Sensors.fuelTurbineSensors
  override val redstoneSensorSystem = Sensors

  lazy val maxOutputs = 6

  def doUpdate() {
    val fuelPerMj = if (fuel.getFluidAmount > 0) 1 / TurbineFuel.getFuelValue(fuel.getFluid.getFluid) / fuelEfficiency else 0
    fuelPerTick := fuelPerMj * maxMJPerTick

    if (getControlStateWithDefault(ControlActions.useFuel, true)) {
      if (burnTime < 5 && fuelPerMj > 0 && maxMJPerTick > 0) {
        val needFuel = Misc.clamp((10 * fuelPerTick).ceil, 0F, fuel.getFluidAmount.toFloat).floor.toInt
        burnTime += needFuel / fuelPerTick
        fuel.drainInternal(needFuel, true)
        fuelPerTickAverage.update(needFuel)
      } else {
        fuelPerTickAverage.update(0)
      }

      if (burnTime > 1 && power.capacity - power.stored > maxMJPerTick) {
        burnTime -= 1
        power.stored += maxMJPerTick
        outputAverage.update(maxMJPerTick.toDouble)
        lastChange = world.getTotalWorldTime
      } else {
        outputAverage.update(0)
      }
    } else {
      outputAverage.update(0)
      fuelPerTickAverage.update(0)
    }
  }

  serverTick.listen(doUpdate)

  override def openGui(player: EntityPlayer) = player.openGui(Generators, cfg.guiId, world, pos.getX, pos.getY, pos.getZ)

  override def getInputTanks: List[IFluidHandler] = List(fuel)

  override def extract(v: Float, simulate: Boolean) = power.extract(v, simulate)

  override def onModulesChanged() {
    fuel.setCapacity(getNumOfModules("FuelTank") * Modules.FuelTank.capacity + cfg.internalFuelCapacity)

    if (fuel.getFluid != null && fuel.getFluidAmount > fuel.getCapacity)
      fuel.getFluid.amount = fuel.getCapacity

    power.capacity = getModuleBlocks[BlockPowerCapacitor].values.map(_.material.mjCapacity).sum.toFloat + cfg.internalPowerCapacity

    if (power.stored > power.capacity)
      power.stored = power.capacity

    val turbines = getModuleBlocks[BlockTurbine].values.map(_.material)
    maxMJPerTick := turbines.map(_.maxMJPerTick).sum.toFloat
    numTurbines := turbines.size

    val hasT1Upgrade = getModulePositions(BlockEfficiencyUpgradeTier1).nonEmpty

    fuelEfficiency := getModulePositions(BlockEfficiencyUpgradeTier2).headOption map { t2 =>
      if (hasT1Upgrade) {
        MachineTurbine.fuelEfficiency.getFloat("Tier2")
      } else {
        world.getTileSafe[TileModule](t2) foreach { tile =>
          tile.coreRemoved()
          moduleRemoved(tile)
        }
        MachineTurbine.fuelEfficiency.getFloat("Base")
      }
    } getOrElse {
      if (hasT1Upgrade) {
        MachineTurbine.fuelEfficiency.getFloat("Tier1")
      } else {
        MachineTurbine.fuelEfficiency.getFloat("Base")
      }
    }

    super.onModulesChanged()
  }

  override def availableControlActions = List(ControlActions.disabled, ControlActions.useFuel)
}
