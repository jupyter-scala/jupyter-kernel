package jupyter.kernel

import com.typesafe.config._

import scala.reflect.runtime.universe

import jupyter.kernel.client.KernelSpecs

package object config {

  trait Module {
    def kernels: Map[String, (Kernel, KernelInfo)]
  }

  def kernelSpecsFromConfig(
    specs: KernelSpecs,
    configName: String,
    classLoader: ClassLoader = Thread.currentThread().getContextClassLoader
  ): Unit = {

    val kernelConfig = ConfigFactory.load(classLoader, configName)

    def configMap(c: Config, path: String): Map[String, ConfigValue] = {
      import scala.collection.JavaConverters._

      if (c.hasPath(path))
        (c.getObject(path): java.util.Map[String, ConfigValue]).asScala.toMap
      else
        Map.empty
    }

    val runtimeMirror = universe.runtimeMirror(classLoader)

    for ((moduleId, obj: ConfigObject) <- configMap(kernelConfig, "jupyter.modules")) {
      val moduleConfig = obj.toConfig

      val module = runtimeMirror.reflectModule(
        runtimeMirror.staticModule(moduleConfig.getString("module"))
      ).instance.asInstanceOf[Module]

      for ((id, (kernel, info)) <- module.kernels)
        specs.add(id, info, kernel)
    }
  }

}
