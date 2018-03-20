package com.rishiqing.test.functional.rest.team

import com.rishiqing.demo.util.http.RsqRestResponse
import com.rishiqing.demo.util.http.RsqRestUtil
import com.rishiqing.test.functional.BaseApi
import com.rishiqing.test.functional.ConfigUtil
import com.rishiqing.test.functional.api.AccountApi
import com.rishiqing.test.functional.api.CorpusApi
import com.rishiqing.test.functional.api.PlanApi
import com.rishiqing.test.functional.api.TeamApi
import com.rishiqing.test.functional.util.db.DomainUtil
import com.rishiqing.test.functional.util.db.SqlPrepare
import com.rishiqing.test.functional.util.db.SqlUtil
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.Unroll

/**
 * 多团队获取团队信息的测试用例
 * user1为team1的创建者
 * user1为team2的管理员
 * user1为team3的普通成员
 * user4为个人用户
 * Created by  on 2017/9/12.Wallace
 */
@Stepwise
class TeamFetchDetailSpec extends BaseApi {
    @Shared def suiteEnv = ConfigUtil.config.suite.teamFetchDetailData

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
        RsqRestUtil.clearCookies();
    }

    @Unroll
    def "注册相关用户：#registerUser"(){
        when: '注册用户'
        RsqRestResponse resp = AccountApi.register(registerUser as Map)
        registerUser.id = resp.jsonMap.id

        then: '验证注册结果'
        AccountApi.checkRegister(resp)

        where:
        registerUser << [
                suiteEnv.teamUser1,
                suiteEnv.teamUser2,
                suiteEnv.teamUser3,
                suiteEnv.teamUser4
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

    /**
     * user-b1：创建team-b1——》创建成功
     * user-b2：创建team-b2——》创建成功
     * user-b3：创建team-b3——》创建成功
     */
    @Unroll
    def "#envTeamCreator 创建公司 #envTeam"(){
        when: '用户登录'
        //  新注册的用户会有生成默认的日程、计划、笔记，这里加1秒的等待时间，保证生成完成
        RsqRestResponse resp = TeamApi.loginAndCreateTeam(
                envTeamCreator as Map,
                envTeam as Map)
        then: '验证登录'
        TeamApi.checkCreateTeam(resp)

        where:
        envTeamCreator << [
                suiteEnv.teamUser1,
                suiteEnv.teamUser2,
                suiteEnv.teamUser3
        ]
        envTeam << [
                suiteEnv.team1ForCreate,
                suiteEnv.team2ForCreate,
                suiteEnv.team3ForCreate
        ]
    }

    /**
     * target：user-1
     * user-2：邀请user-1加入team-b2——》邀请成功
     * user-2：设置user-1为管理员
     */
    def "user2 邀请 #invitedUser 加入 team2 --> #invitedUser 接受邀请并加入 team2，user2 设置 user1 为 team2 的管理员"(){
        given:
        RsqRestResponse resp

        when: 'user1登录邀请user2加入team1'
        resp = TeamApi.loginAndDirectInviteAndJoinInTeam(
                suiteEnv.team2ForCreate as Map,
                suiteEnv.teamUser2 as Map,
                invitedUser as Map)
        then:
        TeamApi.checkSuccess(resp)

        when: '获取user1的公司'
        resp = AccountApi.loginAndFetchUserSiblings(invitedUser as Map)
        then:
        AccountApi.checkFetchUserSiblings(resp, [teamList: [suiteEnv.team1ForCreate, suiteEnv.team2ForCreate], noTeamList: []])


        when: '设置 user1 为管理员'
        Map joinUser = (Map)resp.jsonMap.list.find {it ->
            it.teamName == suiteEnv.team2ForCreate.name
        }
        resp = TeamApi.loginAndSetTeamAdmin(suiteEnv.teamUser2 as Map, joinUser as Map, true)
        then:
        TeamApi.checkSetTeamAdmin(resp)

        where:
        invitedUser << [
                suiteEnv.teamUser1
        ]
    }

    /**
     * teamUser1有三个公司，在这三个公司中角色分别为创建者、管理员和普通成员
     */
    def "teamUser1 获取所在的所有公司信息"(){
        given:
        RsqRestResponse resp

        when: '获取user2的公司'
        resp = AccountApi.loginAndFetchUserSiblings(suiteEnv.teamUser1 as Map)
        then:
        Map orgUser = (Map)resp.jsonMap.list.find {it ->
            it.teamName == suiteEnv.team1ForCreate.name
        }
        Map joinUser = (Map)resp.jsonMap.list.find {it ->
            it.teamName == suiteEnv.team2ForCreate.name
        }

        when: '获取 team1 相关的信息'
        resp = TeamApi.loginAndFetchUserDetail(suiteEnv.teamUser1 as Map, orgUser)
        then:
        Map orgUserResult = resp.jsonMap
        orgUserResult.teamName == suiteEnv.team1ForCreate.name
        orgUserResult.teamRegion == suiteEnv.team1ForCreate.region
        orgUserResult.teamIndustry == suiteEnv.team1ForCreate.industry
        orgUserResult.teamSize == suiteEnv.team1ForCreate.size
        orgUserResult.teamIsCreator == true
        orgUserResult.teamIsAdmin == false

        when: '获取 team2 相关的信息'
        resp = TeamApi.loginAndFetchUserDetail(suiteEnv.teamUser1 as Map, joinUser)
        then:
        Map joinUserResult = resp.jsonMap
        joinUserResult.teamName == suiteEnv.team2ForCreate.name
        joinUserResult.teamRegion == suiteEnv.team2ForCreate.region
        joinUserResult.teamIndustry == suiteEnv.team2ForCreate.industry
        joinUserResult.teamSize == suiteEnv.team2ForCreate.size
        joinUserResult.teamIsCreator == false
        joinUserResult.teamIsAdmin == true
    }
}
