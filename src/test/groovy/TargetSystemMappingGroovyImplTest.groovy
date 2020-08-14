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

    def "isLightInstance"() {
        when:
            def lightInstance = tsmObject.isLightInstance("test-dev-jhe")
            def nonLightInstance = tsmObject.isLightInstance("test-CHEI212")
        then:
            lightInstance == true
            nonLightInstance == false
    }
}