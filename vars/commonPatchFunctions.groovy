#!groovy

import groovy.json.JsonSlurperClassic

def readPatchJsonFileFromStash(def stashName) {
    unstash stashName
    return readJsonFile(new File("${env.WORKSPACE}/PatchFile.json").text)
}

def readJsonFile(def jsonAsText) {
    def json = new JsonSlurperClassic().parseText(jsonAsText)
    json
}

def copyRevisionFilesTo(targetDir) {
    dir(env.REVISIONS_FILES_PATH) {
        fileOperations([
                fileCopyOperation(includes: "Revisions.json", targetLocation: targetDir)
        ])
    }
}

// JHE (06.10.2020): This might/could be centralized somewhere else
def getRevisionFor(service, target, revisionDirPath) {
    println "Getting revision for ${service} and ${target}"
    def jsonFilePath = "${revisionDirPath}/Revisions.json"
    def jsonFile = new File(jsonFilePath)
    if (!jsonFile.exists()) {
        println "${jsonFilePath} does not exist yet, returning empty String"
        return ""
    }
    def json = readJsonFile(jsonFile.text)
    println "Read json from ${jsonFilePath} : ${json}"
    if (json.services."${service.serviceName}" == null || json.services."${service.serviceName}"."${target}" == null) {
        println "No revision ever published for ${service.serviceName} on ${target}, returning empty String"
        return ""
    }
    def revision = json.services."${service.serviceName}"."${target}"
    println "Current Revision for ${service.serviceName} on ${target} = ${revision}"
    return revision
}

def getTargetFor(patchConfig, stageName) {
    def target = "unnkown"
    def stagesMapping = patchConfig.stagesMapping
    stagesMapping.each { stage ->
        if (stage.name.equals(stageName)) {
            target = stage.target
        }
    }
    return target
}

def getStatusCodeFor(patchConfig, target, toStage) {
    def state = "unkown"
    def stageMapping = patchConfig.stagesMapping
    stageMapping.each { sm ->
        if (sm.name.equals(target)) {
            sm.stages.each { stage ->
                if (stage.name.equals(toStage)) {
                    state = stage.code
                }
            }
        }
    }
    return state
}

def notifyDb(patchNumber, stage, successNotification, errorNotification) {
    println "Notifying DB for ${patchNumber} for stage ${stage} with successNotification=${successNotification} and errorNotification=${errorNotification}"
    def cmd = "/opt/apg-patch-cli/bin/apscli.sh ${env.PIPER_URL_PARAMETER} -notifydb ${patchNumber},${stage},${successNotification},${errorNotification}"
    sh "${cmd}"
    println "DONE - ${cmd}"
}

def logPatchActivity(def patchNumber, def target, def step, def logText) {
    def cmd = "/opt/apg-patch-cli/bin/apscli.sh ${env.PIPER_URL_PARAMETER} -log ${patchNumber},${target},${step},${logText}"
    println "Executeing ${cmd}"
    sh "${cmd}"
    println "Executeing ${cmd} done."
}

// Used in order to have Datetime info in our pipelines
def log(msg, caller) {
    def dt = "${new Date().format('yyyy-MM-dd HH:mm:ss.S')}"
    def logMsg = caller != null ? "(${caller}) ${dt}: ${msg}" : "${dt}: ${msg}"
    echo logMsg
}

// Used in order to have Datetime info in our pipelines
def log(msg) {
    log(msg, null)
}

def coFromBranchCvs(cvsBranch, moduleName) {
    def callBack = benchmark()
    def duration = callBack {
        checkout scm: ([$class: 'CVSSCM', canUseUpdate: true, checkoutCurrentTimestamp: false, cleanOnFailedUpdate: false, disableCvsQuiet: false, forceCleanCopy: true, legacy: false, pruneEmptyDirectories: false, repositories: [
                [compressionLevel: -1, cvsRoot: env.CVS_ROOT, excludedRegions: [[pattern: '']], passwordRequired: false, repositoryItems: [
                        [location: [$class: 'BranchRepositoryLocation', branchName: cvsBranch, useHeadIfNotFound: false], modules: [
                                [localName: moduleName, remoteName: moduleName]
                        ]]
                ]]
        ], skipChangeLog      : false])
    }
    log("Checkout of ${moduleName} took ${duration} ms", "coFromBranchCvs")
}

def benchmark() {
    def benchmarkCallback = { closure ->
        start = System.currentTimeMillis()
        closure.call()
        now = System.currentTimeMillis()
        now - start
    }
    benchmarkCallback
}

def createFolder(folderPath) {
    fileOperations([
            folderCreateOperation(folderPath: folderPath)
    ])
}

def deleteFolder(folderPath) {
    fileOperations([
            folderDeleteOperation(folderPath: folderPath)
    ])
}