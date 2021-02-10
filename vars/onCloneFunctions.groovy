#!groovy

def resetRevisionFor(params) {
    deleteDir()
    //TODO JHE (09.02.2020) : HEAD should be taken from Jenkins env variable
    commonPatchFunctions.coFromBranchCvs("HEAD", "cm-resetRevision")
    dir("cm-resetRevision") {
        sh "chmod +x ./gradlew"
        params.buildParameters.each { bp ->
            bp.services.each { s ->
                def cmd = "./gradlew resetRevision -PserviceName=${s.serviceName} -Psrc=${params.src} -Ptarget=${params.target} ${env.GRADLE_OPTS} --info --stacktrace"
                def result = sh(returnStdout: true, script: cmd).trim()
                println "result of ${cmd} : ${result}"
            }
        }
    }
}