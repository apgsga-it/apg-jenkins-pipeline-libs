import groovy.json.JsonSlurperClassic

def printTestMessage(def param) {
    println "This is a test message ... with param = ${param}"
}

def readPatchJsonFile(def jsonFile) {
    def json = new JsonSlurperClassic().parseText(jsonFile.text)
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
    def json = readPatchJsonFile(jsonFile)
    if(json.services."${service.serviceName}" == null || json.services."${service.serviceName}"."${target}" == null) {
        println "No revision ever published for ${service.serviceName} on ${target}, returning empty String"
        return ""
    }
    def revision = json.services."${service.serviceName}"."${target}"
    println "Current Revision for ${service.serviceName} on ${target} = ${revision}"
    return revision
}