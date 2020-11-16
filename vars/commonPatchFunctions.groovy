#!groovy

import groovy.json.JsonSlurperClassic
import groovy.json.JsonBuilder

def readPatchJsonFileFromStash(def stashName) {
    node {
            unstash stashName
            return readJsonFile(new File("${env.WORKSPACE}/PatchFile.json").text)
        }
}

def readJsonFile(def jsonAsText) {
    def json = new JsonSlurperClassic().parseText(jsonAsText)
    json
}

// TODO JHE (06.10.2020): This might/could be centralized somewhere else
def getRevisionFor(service,target) {

    def jsonFilePath = "${env.REVISIONS_FILES_PATH}/Revisions.json"
    def jsonFile = new File(jsonFilePath)
    if(!jsonFile.exists()) {
        println "${jsonFilePath} does not exist yet, returning empty String"
        return ""
    }
    def json = readJsonFile(jsonFile.text)
    if(json.services."${service.serviceName}" == null || json.services."${service.serviceName}"."${target}" == null) {
        println "No revision ever published for ${service.serviceName} on ${target}, returning empty String"
        return ""
    }
    def revision = json.services."${service.serviceName}"."${target}"
    println "Current Revision for ${service.serviceName} on ${target} = ${revision}"
    return revision
}

def getTargetFor(patchConfig,stageName) {
    def target = "unnkown"
    def stagesMapping = patchConfig.stagesMapping
    stagesMapping.each { stage ->
        if(stage.name.equals(stageName)) {
            target = stage.target
        }
    }
    return target
}

def getStatusCodeFor(patchConfig,target,toStage) {
    def state = "unkown"
    def stageMapping = patchConfig.stagesMapping
    stageMapping.each { sm ->
        if(sm.name.equals(target)) {
            sm.stages.each { stage ->
                if(stage.name.equals(toStage)) {
                    state = stage.code
                }
            }
        }
    }
    return state
}

def savePatchConfigState(patchConfig) {
    node {
        println "Saving Patchconfig State ${patchConfig.patchNummer}"
        def patchFileName = "Patch${patchConfig.patchNummer}.json"
        writeFile file: patchFileName , text: new JsonBuilder(patchConfig).toPrettyString()
        // TODO JHE (06.11.2020) : -purl=localhost:9010 should be by default, or provided with parameter
        def cmd = "/opt/apg-patch-cli/bin/apscli.sh -purl localhost:9010 -sa ${patchFileName}"
        println "Executeing ${cmd}"
        sh "${cmd}"
        println "DONE - ${cmd}"
    }
}

def notifyDb(patchConfig,targetToState) {
    node {
        println "Notifying DB for ${patchConfig.patchNummer} in state ${targetToState}"
        // TODO JHE (06.11.2020) : -purl=localhost:9010 should be by default, or provided with parameter
        def cmd = "/opt/apg-patch-cli/bin/apscli.sh -purl localhost:9010 -dbsta ${patchConfig.patchNummer},${targetToState}"
        sh "${cmd}"
        println "DONE - ${cmd}"
    }
}

def logPatchActivity(def patchConfig, def logText) {
    node {
        def cmd = "/opt/apg-patch-cli/bin/apscli.sh -log ${patchConfig.patchNummer},${logText}"
        println "Executeing ${cmd}"
        sh "${cmd}"
        println "Executeing ${cmd} done."
    }
}