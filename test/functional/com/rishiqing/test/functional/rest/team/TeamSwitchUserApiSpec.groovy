package com.rishiqing.test.functional.rest.team

import com.rishiqing.demo.util.http.RsqRestResponse
import com.rishiqing.demo.util.http.RsqRestUtil
import com.rishiqing.test.functional.BaseApi
import com.rishiqing.test.functional.ConfigUtil
import com.rishiqing.test.functional.api.AccountApi
import com.rishiqing.test.functional.api.TeamApi
import com.rishiqing.test.functional.util.db.SqlPrepare
import com.rishiqing.test.functional.util.db.SqlUtil
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

/**
 * 兄弟用户之间切换
 * Created by  on 2017/9/6.Wallace
 */
@Stepwise
/**
 * 不使用switchUser做切换
 */
class TeamSwitchUserApiSpec extends BaseApi {
    @Shared def suiteEnv = ConfigUtil.config.suite.teamSwitchUser

    def setupSpec(){
//        RsqRestUtil.config([proxy: ['127.0.0.1': 5555]])

//          使用SqlPrepare中的genCleanMultiUserUsers生成SQL语句，做账号清理
        if(ConfigUtil.config?.dataSource) {
            String str = SqlPrepare.genCleanMultiUserUsers([
                    domainRegexp: "@${suiteEnv.emailDomain}\$"
            ])
            SqlUtil.execute(str)
        }
    }

    def setup(){
        RsqRestUtil.clearCookies()
    }

    @Unroll
    def "注册相关用户：#registerUser"(){
        when: '注册用户'
        RsqRestResponse resp = AccountApi.register(registerUser as Map)

        then: '验证注册结果'
        AccountApi.checkRegister(resp)

        where:
        registerUser << [
                suiteEnv.teamUser1
        ]
    }
    /**
     * stepwise时暂停3000ms等待用户注册成功
     */
    def "等待3000ms，以便服务器生成默认的日程、计划、笔记等数据"(){
        when:
        Thread.sleep(3000)
        then:
        true
    }

    /**
     * teamUser1 创建 team1ForCreate
     * teamUser1 创建 team2ForCreate
     */
    def "#envTeamCreator 创建 #envTeam"(){
        when: '用户登录'
        RsqRestResponse resp = TeamApi.loginAndCreateTeam(envTeamCreator as Map, envTeam as Map)
        then: '验证登录'
        TeamApi.checkCreateTeam(resp)

        where:
        envTeamCreator << [
                suiteEnv.teamUser1,
                suiteEnv.teamUser1
        ]
        envTeam << [
                suiteEnv.team1ForCreate,
                suiteEnv.team2ForCreate,
        ]
    }

    def "teamUser1 可以在 team1ForCreate 和 team2ForCreate 之间进行切换"(){
        given:
        RsqRestResponse resp

        when:
        resp = AccountApi.loginAndCheck(suiteEnv.teamUser1 as Map)
        resp = AccountApi.switchUserByTeamName(suiteEnv.team1ForCreate as Map)
        resp = AccountApi.fetchLoginInfo()

        then: '当前登录用户为主用户，主用户为teamUser1，即team1ForCreate'
        AccountApi.checkFetchLoginInfo(resp, [team: suiteEnv.team1ForCreate] as Map)

        when:
        resp = AccountApi.switchUserByTeamName(suiteEnv.team2ForCreate as Map)
        resp = AccountApi.fetchLoginInfo()

        then: '当前主用户为teamUser2, 切换用户为team2ForCreate'
        AccountApi.checkFetchLoginInfo(resp, [team: suiteEnv.team2ForCreate] as Map)
    }
}
