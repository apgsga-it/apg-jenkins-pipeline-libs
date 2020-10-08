import groovy.json.JsonSlurperClassic

def readPatchJsonFileFromStash(def stashName) {
    node {
        dir(stashName) {
            unstash stashName

            println "D E B U G"
            def cmd = "pwd"
            def result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
            cmd = "python -mjson.tool ${stashName}.json"
            result = sh ( returnStdout : true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
            println "E N D ------ D E B U G"

            return readJsonFile(new File("PatchFile.json").text)
        }
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
    def json = readPatchJsonFile(jsonFile.text)
    if(json.services."${service.serviceName}" == null || json.services."${service.serviceName}"."${target}" == null) {
        println "No revision ever published for ${service.serviceName} on ${target}, returning empty String"
        return ""
    }
    def revision = json.services."${service.serviceName}"."${target}"
    println "Current Revision for ${service.serviceName} on ${target} = ${revision}"
    return revision
}