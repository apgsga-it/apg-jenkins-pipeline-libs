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
    def jsonFile = new File("/var/jenkins/gradle/home/Revisions.json")
    def json = readPatchJsonFile(jsonFile)
    def revision = json.services."${service.serviceName}"."${target}"
    println "Current Revision for ${service.serviceName} on ${target} = ${revision}"
    return revision
}