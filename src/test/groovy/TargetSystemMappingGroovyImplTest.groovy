import spock.lang.Specification

class TargetSystemMappingGroovyImplTest extends Specification {

    def tsmClassFilePath = "resources/targetSystemMappings.groovy"
    def tsmJson = "src/test/resources/TargetSystemMappings.json"
    def tsmObject

    def setup() {
        def gs = new GroovyShell()
        def script = gs.parse(new File(tsmClassFilePath))
        tsmObject = script.create(tsmJson)
    }

    def "test serviceTypeFor"() {
        when:
            def linuxServiceType = tsmObject.serviceTypeFor("jadas","test-CHTI211")
            def guiServiceType = tsmObject.serviceTypeFor("it21_ui","test-CHEI211")
        then:
            linuxServiceType.equals("linuxservice")
            guiServiceType.equals("linuxbasedwindowsfilesystem")
    }

    def "test installTargetFor"() {
        when:
            def linuxServiceType = tsmObject.installTargetFor("jadas","test-CHTI211")
            def guiServiceType = tsmObject.installTargetFor("it21_ui","test-CHEI211")
        then:
            linuxServiceType == "test-jadas-t.apgsga.ch"
            guiServiceType == "test-service-chei211.apgsga.ch"
    }

    def "test isLightInstance"() {
        when:
            def lightInstance = tsmObject.isLightInstance("test-dev-jhe")
            def nonLightInstance = tsmObject.isLightInstance("test-CHEI212")
        then:
            lightInstance == true
            nonLightInstance == false
    }

    def "test validToStates"() {
        when:
            def vts = tsmObject.validToStates()
        then:
            vts.size() == 8
            vts.contains("Entwicklung")
            vts.contains("Informatiktest")
            vts.contains("Anwendertest")
            vts.contains("Produktion")
            vts.contains("EntwicklungInstallationsbereit")
            vts.contains("InformatiktestInstallationsbereit")
            vts.contains("AnwendertestInstallationsbereit")
            vts.contains("ProduktionInstallationsbereit")
    }

    def "test listInstallTargets"() {
        when:
            def lit = tsmObject.listInstallTargets()
        then:
            lit.size() == 4
            lit.contains("test-CHEI211")
            lit.contains("test-CHEI212")
            lit.contains("test-CHTI212")
            lit.contains("test-devjhe")
    }

    def "test stateMap"() {
        when:
            def stateMap = tsmObject.stateMap()
        then:
            stateMap != null
            stateMap.size() == 8
            def keys = stateMap.keySet()
            keys.contains("Entwicklung")
            keys.contains("Informatiktest")
            keys.contains("Anwendertest")
            keys.contains("Produktion")
            keys.contains("EntwicklungInstallationsbereit")
            keys.contains("InformatiktestInstallationsbereit")
            keys.contains("AnwendertestInstallationsbereit")
            keys.contains("ProduktionInstallationsbereit")
            // Entwicklung entries
            stateMap.get("Entwicklung").get("targetName").equals("Entwicklung")
            stateMap.get("Entwicklung").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("Entwicklung").get("stage").equals("cancel")
            stateMap.get("Entwicklung").get("target").equals("test-CHEI212")
            // Informatiktest entries
            stateMap.get("Informatiktest").get("targetName").equals("Informatiktest")
            stateMap.get("Informatiktest").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("Informatiktest").get("stage").equals("InstallFor")
            stateMap.get("Informatiktest").get("target").equals("test-CHEI211")
            // Anwendertest entries
            stateMap.get("Anwendertest").get("targetName").equals("Anwendertest")
            stateMap.get("Anwendertest").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("Anwendertest").get("stage").equals("InstallFor")
            stateMap.get("Anwendertest").get("target").equals("test-CHTI211")
            // Produktion entries
            stateMap.get("Produktion").get("targetName").equals("Produktion")
            stateMap.get("Produktion").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("Produktion").get("stage").equals("InstallFor")
            stateMap.get("Produktion").get("target").equals("test-CHPI211")
            // EntwicklungInstallationsbereit entries
            stateMap.get("EntwicklungInstallationsbereit").get("targetName").equals("Entwicklung")
            stateMap.get("EntwicklungInstallationsbereit").get("clsName").equals("com.apgsga.microservice.patch.server.impl.EntwicklungInstallationsbereitAction")
            stateMap.get("EntwicklungInstallationsbereit").get("stage").equals("startPipelineAndTag")
            stateMap.get("EntwicklungInstallationsbereit").get("target").equals("test-CHEI212")
            // InformatiktestInstallationsbereit entries
            stateMap.get("InformatiktestInstallationsbereit").get("targetName").equals("Informatiktest")
            stateMap.get("InformatiktestInstallationsbereit").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("InformatiktestInstallationsbereit").get("stage").equals("BuildFor")
            stateMap.get("InformatiktestInstallationsbereit").get("target").equals("test-CHEI211")
            // AnwendertestInstallationsbereit entries
            stateMap.get("AnwendertestInstallationsbereit").get("targetName").equals("Anwendertest")
            stateMap.get("AnwendertestInstallationsbereit").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("AnwendertestInstallationsbereit").get("stage").equals("BuildFor")
            stateMap.get("AnwendertestInstallationsbereit").get("target").equals("test-CHTI211")
            // ProduktionInstallationsbereit entries
            stateMap.get("ProduktionInstallationsbereit").get("targetName").equals("Produktion")
            stateMap.get("ProduktionInstallationsbereit").get("clsName").equals("com.apgsga.microservice.patch.server.impl.PipelineInputAction")
            stateMap.get("ProduktionInstallationsbereit").get("stage").equals("BuildFor")
            stateMap.get("ProduktionInstallationsbereit").get("target").equals("test-CHPI211")
    }
}