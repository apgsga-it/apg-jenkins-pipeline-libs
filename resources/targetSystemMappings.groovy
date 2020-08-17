import groovy.json.JsonSlurper
import org.springframework.util.Assert

import java.nio.file.Files

def static create(def tsmFilePath) {
    File tsmFile = new File(tsmFilePath)
    Assert.isTrue(Files.exists(tsmFile.toPath()),"${tsmFilePath} does not exist!!")
    def tsmInstance = TargetSystemMappings.instance
    tsmInstance.setTsmFile(tsmFile)
    tsmInstance
}

@Singleton()
class  TargetSystemMappings {

    private File tsmFile
    def setTsmFile(def tsmFile){
        this.tsmFile = tsmFile
    }

    def findStatus(String toStatus) {
        //TODO JHE, not sure what we want here
    }

    def serviceTypeFor(String serviceName, String target) {
        def targetInstances = loadTargetInstances(tsmFile.text)
        return targetInstances."${target}".find{service -> service.name.equalsIgnoreCase(serviceName)}.type
    }

    def installTargetFor(String serviceName, String target) {
        def targetInstances = loadTargetInstances(tsmFile.text)
        return targetInstances."${target}".find{service -> service.name.equalsIgnoreCase(serviceName)}.host
    }

    def isLightInstance(String target) {
        def targetInstances = loadTargetInstances(tsmFile.text)
        // JHE (12.08.2020): For now, we check if "light" is in host name of the DB service
        return targetInstances."${target}".find{service -> service.name.equalsIgnoreCase("it21-db")}.host.contains("light")
    }

    def validToStates() {
        def vts = []
        def stageMapping = loadStageMapping(tsmFile.text)
        stageMapping.keySet().each { stage ->
            stageMapping.get(stage).each { toStage ->
                // Ensure we store String, and not GString
                vts.add((String)"${stage}${toStage.toState}")
            }
        }
        vts
    }

    def listInstallTargets() {
        def targetSystemMappingAsJson = new JsonSlurper().parseText(tsmFile.text)
        def odt = []
        // Ensure we return a list of String, and not GString
        targetSystemMappingAsJson.onDemandTarget.each{target -> odt.add((String)target)}
        odt
    }

    def stateMap() {
        def stateMap = [:]
        def targetSystemMappingAsJson = new JsonSlurper().parseText(tsmFile.text)
        targetSystemMappingAsJson.stageMappings.find({ a ->
            a.stages.find({
                def m = [:]
                m.put("targetName",(String)"${a.name}")
                m.put("clsName",(String)"${it.implcls}")
                m.put("stage",(String)"${it.name}")
                m.put("target",(String)"${a.target}")
                stateMap.put((String)"${a.name}${it.toState}",m)
            })
        })
        stateMap
    }

    private def loadStageMapping(targetSystemMappingAsText) {
        def stageMapping = [:]
        def targetSystemMappingAsJson = new JsonSlurper().parseText(targetSystemMappingAsText)
        targetSystemMappingAsJson.stageMappings.each( {stage ->
            stageMapping.put(stage.name,stage.stages)
        })
        stageMapping
    }

    private def loadTargetInstances(targetSystemMappingAsText) {
        def targetInstances = [:]
        def targetSystemMappingAsJson = new JsonSlurper().parseText(targetSystemMappingAsText)
        targetSystemMappingAsJson.targetInstances.each( {targetInstance ->
            targetInstances.put(targetInstance.name,targetInstance.services)
        })
        targetInstances
    }
}