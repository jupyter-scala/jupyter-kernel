package jupyter.kernel

import com.typesafe.config._
import jupyter.kernel.client.KernelSpecs

package object config {
  trait Module {
    def kernels: Map[String, (Kernel, KernelInfo)]
  }

  def kernelSpecsFromConfig(specs: KernelSpecs, configName: String, classLoader: ClassLoader = getClass.getClassLoader): Unit = {
    val kernelConfig = ConfigFactory.load(classLoader, configName)

    def configMap(c: Config, path: String): Map[String, ConfigValue] = {
      import scala.collection.JavaConverters._

      if (c.hasPath(path))
        (c.getObject(path): java.util.Map[String, ConfigValue]).asScala.toMap
      else
        Map.empty
    }

    val runtimeMirror = scala.reflect.runtime.universe runtimeMirror classLoader
    import runtimeMirror.{reflectModule, staticModule}

    for ((moduleId, v: ConfigObject) <- configMap(kernelConfig, "jupyter.modules")) {
      val c = v.toConfig
      val module = reflectModule(staticModule(c getString "module")).instance.asInstanceOf[Module]

      for ((id, (kernel, info)) <- module.kernels)
        specs.add(id, info, kernel)
    }
  }
}
