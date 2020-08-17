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
}