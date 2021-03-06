#!groovy

def resetRevisionFor(params) {
    deleteDir()
    getResetRevisionGradleFile()
    params.buildParameters.each { bp ->
        bp.services.each { s ->
            def cmd = "/opt/gradle/bin/gradle resetRevision -PserviceName=${s.serviceName} -Psrc=${params.src} -Ptarget=${params.target} ${env.GRADLE_OPTS} --info --stacktrace"
            def result = sh(returnStdout: true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }
    }
}

def getResetRevisionGradleFile() {
    // JHE (10.02.2021) : No idea why libraryResource function doesn't work. As a workaround, we clone the repo and take what we want from there
    //                    Probably a wget from following would also work: https://raw.githubusercontent.com/apgsga-it/apg-jenkins-pipeline-libs/${env.GITHUB_JENKINS_VERSION}/resources/build.gradle.resetRevision
    def cmd = "git clone -b ${env.GITHUB_JENKINS_VERSION} https://github.com/apgsga-it/apg-jenkins-pipeline-libs.git"
    def result = sh(returnStdout: true, script: cmd).trim()
    println "result of ${cmd} : ${result}"
    def renameCmd = "cp apg-jenkins-pipeline-libs/resources/build.gradle.resetRevision build.gradle"
    def renameCmdResult = sh(returnStdout: true, script: renameCmd).trim()
    println "result of ${renameCmd} : ${renameCmdResult}"
}

def logAssembleAndDeployPatchActivity(params,logText,buildUrl) {
    def patchNumberList = params.patchNumbers
    def target = params.target
    commonPatchFunctions.log("Logging patch activity for ${patchNumberList}","onCloneFunctions.logAssembleAndDeployPatchActivity")
    patchNumberList.each{patchNumber ->
        commonPatchFunctions.logPatchActivity(patchNumber, target, "assembleAndDeploy", logText,buildUrl)
    }
}