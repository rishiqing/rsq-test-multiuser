package com.rishiqing.test.functional.rest.team

import com.rishiqing.demo.util.http.RsqRestResponse;
import com.rishiqing.demo.util.http.RsqRestUtil;
import com.rishiqing.test.functional.BaseApi;
import com.rishiqing.test.functional.ConfigUtil
import com.rishiqing.test.functional.api.AccountApi
import com.rishiqing.test.functional.api.TeamApi;
import com.rishiqing.test.functional.util.db.SqlPrepare;
import com.rishiqing.test.functional.util.db.SqlUtil;
import spock.lang.Shared;
import spock.lang.Stepwise
import spock.lang.Unroll;

@Stepwise
public class TeamQuitApiSpec  extends BaseApi {
    @Shared
    def suiteEnv = ConfigUtil.config.suite.teamQuit

    def setupSpec(){
//        RsqRestUtil.config([proxy: ['127.0.0.1': 5555]])

//          使用SqlPrepare中的genCleanMultiUserUsers生成SQL语句，做账号清理
        if(ConfigUtil.config?.dataSource) {
            SqlUtil.execute(SqlPrepare.genCleanMultiUserUsers([
                    domainRegexp: "@${suiteEnv.emailDomain}\$"
            ]))
        }
    }

    def setup(){
        RsqRestUtil.clearCookies()
    }

    @Unroll
    def "注册相关的用户：#registerUser"(){
        when: '注册用户'
        RsqRestResponse resp = AccountApi.register(registerUser as Map)

        then: '验证注册结果'
        AccountApi.checkRegister(resp)

        where:
        registerUser << [
                suiteEnv.teamUser1,
                suiteEnv.teamUser2
        ]
    }

    /**
     * stepwise时暂停3000ms等待用户注册成功
     * 新注册的用户会有生成默认的日程、计划、笔记，这里加1秒的等待时间，保证生成完成
     */
    def "等待3000ms，以便服务器生成默认的日程、计划、笔记等数据"(){
        when:
        Thread.sleep(3000)
        then:
        true
    }

    @Unroll
    def "#teamUser 创建 #teamForCreate"(){
        given:
        RsqRestResponse resp

        when: 'teamUser创建teamForCreate'
        resp = TeamApi.loginAndCreateTeam(teamUser as Map, teamForCreate as Map)
        then:
        TeamApi.checkCreateTeam(resp)

        where:
        teamUser << [
                suiteEnv.teamUser1,
                suiteEnv.teamUser2
        ]
        teamForCreate << [
                suiteEnv.team1ForCreate,
                suiteEnv.team2ForCreate
        ]
    }

    def "teamUser2 邀请 teamUser1 加入，teamUser1 接受邀请，成功加入 team2ForCreate ，teamUser1 退出 team2ForCreate"(){
        given:
        RsqRestResponse resp

        when: 'teamUser2 邀请 teamUser1 加入 team2ForCreate'
        resp = TeamApi.loginAndDirectInviteAndJoinInTeam(
                suiteEnv.team2ForCreate as Map,
                suiteEnv.teamUser2 as Map,
                suiteEnv.teamUser1 as Map)
        then:
        TeamApi.checkJoinInTeam(resp)
        AccountApi.logoutAndCheck()

        when: 'teamUser1 登录，设置team2ForCreate 为主团队'
        resp = AccountApi.login(suiteEnv.teamUser1 as Map)
        resp = AccountApi.switchUserByTeamName(suiteEnv.team2ForCreate as Map)
        resp = AccountApi.fetchLoginInfo()

        then:
        AccountApi.checkFetchLoginInfo(resp, [team: suiteEnv.team2ForCreate])

        when: 'teamUser1 退出团队 team2forCreate'
        resp = TeamApi.quitTeam()
        then:
        TeamApi.checkQuitTeam(resp)

        when: 'teamUser1 有两个user, 只有一个team'
        resp = AccountApi.fetchUserSiblings()

        then:
        AccountApi.checkFetchUserSiblings(resp, [
                teamList: [suiteEnv.team1ForCreate as Map],
                noTeamList: [[:]]
        ])
    }
}
