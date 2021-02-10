#!groovy

def resetRevisionFor(params) {
    deleteDir()
    getResetRevisionGradleFile()
    params.buildParameters.each { bp ->
        bp.services.each { s ->
            def cmd = "gradle resetRevision -PserviceName=${s.serviceName} -Psrc=${params.src} -Ptarget=${params.target} ${env.GRADLE_OPTS} --info --stacktrace"
            def result = sh(returnStdout: true, script: cmd).trim()
            println "result of ${cmd} : ${result}"
        }
    }
}

def getResetRevisionGradleFile() {
    // JHE (10.02.2021) : No idea why libraryResource function doesn't work. As a workaround, we're getting the resource using wget
    def cmd = "wget https://github.com/apgsga-it/apg-jenkins-pipeline-libs/tree/${env.GITHUB_JENKINS_VERSION}/resources/build.gradle.resetRevision -o build.gradle"
    def result = sh(returnStdout: true, script: cmd).trim()
    println "result of ${cmd} : ${result}"
}