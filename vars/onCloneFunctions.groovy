#!groovy

import groovy.json.JsonSlurperClassic

def resetRevisionFor(params) {
    deleteDir()
    commonPatchFunctions.coFromBranchCvs("HEAD","cm-resetRevision")

    println "params = ${params}"

    dir("cm-resetRevision") {
        sh "chmod +x ./gradlew"
        def cmd = "./gradlew ./gradlew resetRevision -PserviceName=echoservice -Psrc=chei211 -Ptarget=CHEI212 ${env.GRADLE_OPTS} --info --stacktrace"
        def result = sh ( returnStdout : true, script: cmd).trim()
        println "result of ${cmd} : ${result}"
    }

}