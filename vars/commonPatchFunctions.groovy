import groovy.json.JsonSlurperClassic

def printTestMessage(def param) {
    println "This is a test message ... with param = ${param}"
}

def readPatchJsonFile(def jsonFile) {
    def patchConfig = new JsonSlurperClassic().parseText(jsonFile.text)
    patchConfig
}