#!groovy

import groovy.json.JsonSlurperClassic

def resetRevisionFor(params) {
    deleteDir()
    commonPatchFunctions.coFromBranchCvs("HEAD","cm-resetRevision")

    println "params = ${params}"

    params.patches.each{ p ->
        p.services.each { s ->
            println "Here we should call the gradle project with : -PserviceName=${s.serviceName} / -Psrc=${params.src} / -Ptarget=${params.target}"
        }
    }

    dir("cm-resetRevision") {
        sh "chmod +x ./gradlew"
        def cmd = "./gradlew resetRevision -PserviceName=echoservice -Psrc=chei211 -Ptarget=CHEI212 ${env.GRADLE_OPTS} --info --stacktrace"
        def result = sh ( returnStdout : true, script: cmd).trim()
        println "result of ${cmd} : ${result}"
    }

}