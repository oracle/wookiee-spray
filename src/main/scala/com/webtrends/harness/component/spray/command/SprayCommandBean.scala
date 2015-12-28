package com.webtrends.harness.component.spray.command

import com.webtrends.harness.command.CommandBean

class SprayCommandBean(var authInfo: Option[(String, String)]) extends CommandBean {
  def setAuthInfo(info: Option[(String, String)]) {
    authInfo = info
  }
}
