node('linux') {
  stage ('Poll') {
    checkout([
      $class: 'GitSCM', branches: [[name: '*/main']], extensions: [],
      userRemoteConfigs: [[url: 'https://github.com/zopencommunity/syftport.git']]])
  }
  stage('Build') {
    build job: 'Port-Pipeline', parameters: [
      string(name: 'PORT_GITHUB_REPO', value: 'https://github.com/zopencommunity/syftport.git'),
      string(name: 'PORT_DESCRIPTION', value: 'A CLI tool and Go library for generating a Software Bill of Materials (SBOM) from container images and filesystems'),
      string(name: 'BUILD_LINE', value: 'DEV')
    ]
  }
}
