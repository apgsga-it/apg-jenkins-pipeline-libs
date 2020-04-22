#!/bin/bash
show_help() {
  cat <<EOF
Usage: ${0##*/} -m COMMITMSG [-b BRANCH]
Build and publishToMavenLocal , git commit with COMMITMSG git push to a optional BRANCH

    -h          display this help and exit
    -m=COMMITMSG Commit Message Text
    -b=BRANCH   git branch, defaults to master

EOF
}
set -o errexit -o pipefail -o noclobber -o nounset
# -allow a command to fail with !’s side effect on errexit
# -use return value from ${PIPESTATUS[0]}, because ! hosed $?
! getopt --test >/dev/null
if [[ ${PIPESTATUS[0]} -ne 4 ]]; then
  echo "I’m sorry, $(getopt --test) failed in this environment, see if you install gnu-getopt "
  exit 1
fi
#Defaults
BRANCH="master"
COMMITMSG=

#Command line Options
OPTIONS=hm:b:
LONGOPTS=help,message:,branch:

# -regarding ! and PIPESTATUS see above
# -temporarily store output to be able to check for errors
# -activate quoting/enhanced mode (e.g. by writing out “--options”)
# -pass arguments only via   -- "$@"   to separate them correctly
! PARSED=$(getopt --options=$OPTIONS --longoptions=$LONGOPTS --name "$0" -- "$@")
if [[ ${PIPESTATUS[0]} -ne 0 ]]; then
  show_help
  exit 0
fi
# read getopt’s output this way to handle the quoting right:
eval set -- "$PARSED"
while true; do
  case "$1" in
  -h | --help)
    show_help
    exit 0
    ;;
  -m | --message)
    COMMITMSG=$2
    shift 2
    ;;
  -b | --branch)
    BRANCH=$2
    shift 2
    ;;
  --)
    shift
    break
    ;;
  *)
    show_help >&2
    exit 1
    ;;
  esac
done

# Preconditions
git --version >/dev/null 2>&1 || {
  echo >&2 "git is either not in Path or Installed.  Aborting."
  exit 1
}
if [ -z "$COMMITMSG" ]; then
  echo >&2 "Commit Message not provided.  Aborting."
  show_help >&2
  exit 1
fi
git diff
echo "Running with Commit Msg=$COMMITMSG and Branch=$BRANCH, ok?"
read ackn
test="$(tr [A-Z] [a-z] <<< "$ackn")"
if [ "$test" != "y" ] ; then
  echo  "Ok, doing nothing and terminating."
  exit 0
fi
./gradlew clean build publishToMavenLocal --info --stacktrace
git add -A
git commit -m "$COMMITMSG"
git push origin "$BRANCH"