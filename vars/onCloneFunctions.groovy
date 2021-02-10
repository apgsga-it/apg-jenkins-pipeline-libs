#!groovy

def resetRevisionFor(params) {

    deleteDir()
    def buildGradle = libraryResource("build.gradle.resetRevision")
    writeFile(file: "build.gradle", text: buildGradle)
    params.buildParameters.each { bp ->
        bp.services.each { s ->
            def cmd = "gradle resetRevision -PserviceName=${s.serviceName} -Psrc=${params.src} -Ptarget=${params.target} ${env.GRADLE_OPTS} --info --stacktrace"
            def result = sh(returnStdout: true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }
    }

    /*
    deleteDir()
    commonPatchFunctions.coFromBranchCvs(env.CM_RESET_REV_PROJECT_CVS_BRANCH, "cm-resetRevision")
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

     */
}