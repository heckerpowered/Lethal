package heckerpowered.lethal.gameplay

import heckerpowered.lethal.platform.IdentifierInterop
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import org.apache.logging.log4j.Logger

@Mod(
    modid = LethalMod.MOD_ID,
    name = LethalMod.NAME,
    version = LethalMod.VERSION,
    // modLanguageAdapter = "net.shadowfacts.forgelin.KotlinAdapter"
)
class LethalMod {
    companion object {
        const val MOD_ID = "lethal"
        const val NAME = "Lethal Mod"
        const val VERSION = "1.0"

        @JvmStatic
        lateinit var Logger: Logger

        @JvmStatic
        fun resource(path: String): ResourceLocation {
            return ResourceLocation(MOD_ID, path)
        }
    }

    @EventHandler
    fun preInitialize(event: FMLPreInitializationEvent) {
        Logger = event.modLog

        val native = ResourceLocation("lethal", "blood_sword")
        val bridge = IdentifierInterop.identifier(native)
        val back = IdentifierInterop.identifier(bridge)

        println("native -> bridge class = ${bridge.javaClass.name}")
        println("bridge -> native class = ${back.javaClass.name}")
        println("same native object = ${native === back}")
        println("bridge namespace = ${bridge.namespace}")
        println("bridge path = ${bridge.path}")
    }
}