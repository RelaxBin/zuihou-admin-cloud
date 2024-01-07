package top.tangyh.lamp.oauth.biz;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import top.tangyh.basic.context.ContextUtil;
import top.tangyh.basic.jackson.JsonUtil;
import top.tangyh.basic.utils.BeanPlusUtil;
import top.tangyh.basic.utils.CollHelper;
import top.tangyh.basic.utils.StrPool;
import top.tangyh.basic.utils.TreeUtil;
import top.tangyh.lamp.base.service.system.BaseRoleService;
import top.tangyh.lamp.base.vo.result.user.RouterMeta;
import top.tangyh.lamp.base.vo.result.user.VueRouter;
import top.tangyh.lamp.common.constant.BizConstant;
import top.tangyh.lamp.common.constant.RoleConstant;
import top.tangyh.lamp.model.enumeration.HttpMethod;
import top.tangyh.lamp.model.enumeration.system.ResourceTypeEnum;
import top.tangyh.lamp.system.entity.application.DefResource;
import top.tangyh.lamp.system.entity.application.DefResourceApi;
import top.tangyh.lamp.system.enumeration.tenant.ResourceOpenWithEnum;
import top.tangyh.lamp.system.service.application.DefResourceService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 资源大业务
 *
 * @author zuihou
 * @date 2021/10/29 19:42
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceBiz {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private final DefResourceService defResourceService;
    private final BaseRoleService baseRoleService;

    /**
     * 是否所有的子都是视图
     */
    private static boolean hideChildrenInMenu(List<VueRouter> children) {
        if (CollUtil.isEmpty(children)) {
            return false;
        }

        return children.stream().noneMatch(item -> ResourceTypeEnum.MENU.eq(item.getResourceType()));
    }

    /**
     * 查询当前用户可用的 按钮 + 字段 + 视图
     *
     * @param applicationId 应用id
     * @return 资源树
     */
    public List<String> findVisibleResource(Long employeeId, Long applicationId) {
        List<DefResource> list;
        boolean isAdmin = baseRoleService.checkRole(employeeId, RoleConstant.TENANT_ADMIN);
        List<String> resourceCodes = Arrays.asList(ResourceTypeEnum.BUTTON.getCode(), ResourceTypeEnum.VIEW.getCode(), ResourceTypeEnum.FIELD.getCode());
        if (isAdmin) {
            // 管理员 拥有所有权限，查询指定应用，指定类型的 所有资源
            list = defResourceService.findResourceListByApplicationId(applicationId != null ? Collections.singletonList(applicationId) : Collections.emptyList(), resourceCodes);
        } else {
            List<Long> resourceIdList = baseRoleService.findResourceIdByEmployeeId(applicationId, employeeId);
            if (resourceIdList.isEmpty()) {
                return Collections.emptyList();
            }

            list = defResourceService.findByIdsAndType(resourceIdList, resourceCodes);
        }
        return CollHelper.split(list, DefResource::getCode, StrPool.SEMICOLON);
    }

    public List<VueRouter> findAllVisibleRouter(Long employeeId, String subGroup) {
        List<DefResource> list;
        boolean isAdmin = baseRoleService.checkRole(employeeId, RoleConstant.TENANT_ADMIN);
        List<String> menuCodes = Arrays.asList(ResourceTypeEnum.MENU.getCode(), ResourceTypeEnum.VIEW.getCode());
        if (isAdmin) {
            // 管理员 拥有所有权限，查询指定应用，指定类型的 所有路由
            list = defResourceService.findResourceListByApplicationId(Collections.emptyList(), menuCodes);
        } else {
            List<Long> resourceIdList = baseRoleService.findResourceIdByEmployeeId(null, employeeId);
            if (resourceIdList.isEmpty()) {
                return Collections.emptyList();
            }

            list = defResourceService.findByIdsAndType(resourceIdList, menuCodes);
        }

        if (StrUtil.isNotEmpty(subGroup)) {
            list = list.stream().filter(item -> subGroup.equals(item.getSubGroup())).toList();
        }

        List<VueRouter> routers = BeanPlusUtil.copyToList(list, VueRouter.class);
        List<VueRouter> tree = TreeUtil.buildTree(routers);
        forEachTree(tree, 1);
        return tree;
    }

    /**
     * 查询当前用户可用的 菜单 + 视图
     * <p>
     * 1. 租户管理员拥有指定应用的所有 资源
     * 2. 还没成为员工的用户，没有任何 资源
     *
     * @param applicationId 应用id
     * @return 资源树
     */
    public List<VueRouter> findVisibleRouter(Long applicationId, Long employeeId, String subGroup) {
        List<DefResource> list;
        boolean isAdmin = baseRoleService.checkRole(employeeId, RoleConstant.TENANT_ADMIN);
        List<String> menuCodes = Arrays.asList(ResourceTypeEnum.MENU.getCode(), ResourceTypeEnum.VIEW.getCode());
        if (isAdmin) {
            // 管理员 拥有所有权限，查询指定应用，指定类型的 所有路由
            list = defResourceService.findResourceListByApplicationId(applicationId != null ? Collections.singletonList(applicationId) : Collections.emptyList(), menuCodes);
        } else {
            List<Long> resourceIdList = baseRoleService.findResourceIdByEmployeeId(applicationId, employeeId);
            if (resourceIdList.isEmpty()) {
                return Collections.emptyList();
            }

            list = defResourceService.findByIdsAndType(resourceIdList, menuCodes);
        }

        if (StrUtil.isNotEmpty(subGroup)) {
            list = list.stream().filter(item -> subGroup.equals(item.getSubGroup())).toList();
        }

        List<VueRouter> routers = BeanPlusUtil.copyToList(list, VueRouter.class);
        List<VueRouter> tree = TreeUtil.buildTree(routers);
        forEachTree(tree, 1);
        return tree;
    }

    /**
     * 检查指定接口是否有访问权限
     *
     * @param path   请求路径
     * @param method 请求方法
     * @return 是否有权限
     */
    public Boolean checkUri(String path, String method) {
        Long employeeId = ContextUtil.getEmployeeId();
        Long applicationId = ContextUtil.getApplicationId();
        log.info("path={}, method={}, employeeId={}, applicationId={}", path, method, employeeId, applicationId);
        if (StrUtil.isEmpty(path) || StrUtil.isEmpty(method)) {
            return false;
        }
        boolean isAdmin = baseRoleService.checkRole(employeeId, RoleConstant.TENANT_ADMIN);
        List<DefResourceApi> apiList;
        if (isAdmin) {
            // 方案2 查db
            long queryResStart = System.currentTimeMillis();
            apiList = defResourceService.findResourceApi(applicationId != null ? Collections.singletonList(applicationId) : Collections.emptyList(), Collections.emptyList());
            long queryResEnd = System.currentTimeMillis();
            log.info("biz query 校验api权限:{} - {}  耗时:{}", path, method, (queryResEnd - queryResStart));
        } else {
            // 普通用户 需要校验 uri + method 的权限
            long queryResStart = System.currentTimeMillis();
            List<Long> resourceIdList = baseRoleService.findResourceIdByEmployeeId(applicationId, employeeId);
            if (resourceIdList.isEmpty()) {
                return false;
            }
            long queryResEnd = System.currentTimeMillis();
            apiList = defResourceService.findApiByResourceId(resourceIdList);
            long queryApiEnd = System.currentTimeMillis();
            log.info("biz query 校验api权限:{} - {}  耗时:{}, {}", path, method, (queryResEnd - queryResStart), (queryApiEnd - queryResEnd));
        }

        if (apiList.isEmpty()) {
            return false;
        }

        long apiStart = System.currentTimeMillis();
        boolean flag = apiList.parallelStream().distinct().anyMatch(item -> {
            String uri = item.getUri();
            if (StrUtil.equalsIgnoreCase(uri, path)) {
                if (StrUtil.equalsIgnoreCase(method, item.getRequestMethod()) || HttpMethod.ALL.name().equalsIgnoreCase(item.getRequestMethod())) {
                    return true;
                }
            }

            boolean matchUri = PATH_MATCHER.match(StrUtil.trim(uri), StrUtil.trim(path));

            log.debug("path={}, uri={}, matchUri={}, method={} apiId={}", path, uri, matchUri, item.getRequestMethod(), item.getId());
            if (HttpMethod.ALL.name().equalsIgnoreCase(item.getRequestMethod())) {
                return matchUri;
            }
            return matchUri && StrUtil.equalsIgnoreCase(method, item.getRequestMethod());
        });
        long apiEnd = System.currentTimeMillis();
        log.info("biz list 校验api权限:{} - {}  耗时:{}", path, method, (apiEnd - apiStart));
        return flag;
    }

    private void forEachTree(List<VueRouter> tree, int level) {
        if (CollUtil.isEmpty(tree)) {
            return;
        }
        for (VueRouter item : tree) {
            log.debug("level={}, label={}", level, item.getName());
            RouterMeta meta = null;
            if (StrUtil.isNotEmpty(item.getMetaJson()) && !StrPool.BRACE.equals(item.getMetaJson())) {
                meta = JsonUtil.parse(item.getMetaJson(), RouterMeta.class);
            }
            if (meta == null) {
                meta = new RouterMeta();
            }
            if (StrUtil.isEmpty(meta.getTitle())) {
                meta.setTitle(item.getName());
            }
            meta.setIcon(item.getIcon());
            if (ResourceOpenWithEnum.INNER_CHAIN.eq(item.getOpenWith())) {
                //  是否内嵌页面
                meta.setFrameSrc(item.getComponent());
                item.setComponent(BizConstant.IFRAME);
            } else if (ResourceOpenWithEnum.OUTER_CHAIN.eq(item.getOpenWith())) {
                // 是否外链
                item.setComponent(BizConstant.IFRAME);
            }

            // 视图需要隐藏
            if (ResourceTypeEnum.VIEW.eq(item.getResourceType())) {
                meta.setHideMenu(true);
            }
            // 是否所有的子都是视图
            meta.setHideChildrenInMenu(hideChildrenInMenu(item.getChildren()));
            item.setMeta(meta);

            // 若当前菜单的 子菜单至少有一个菜单，将它设置为 LAYOUT
            if (CollUtil.isNotEmpty(item.getChildren()) && !hideChildrenInMenu(item.getChildren())) {
                item.setComponent(BizConstant.LAYOUT);
            }

            if (CollUtil.isNotEmpty(item.getChildren())) {
                forEachTree(item.getChildren(), level + 1);
            }
        }
    }

    /**
     * 检测员工是否拥有指定应用的权限
     *
     * @param applicationId 应用id
     * @param employeeId    员工id
     * @return 是否拥有指定应用的权限
     */
    public Boolean checkEmployeeHaveApplication(Long employeeId, Long applicationId) {
        boolean isAdmin = baseRoleService.checkRole(employeeId, RoleConstant.TENANT_ADMIN);
        if (isAdmin) {
            return true;
        }
        return CollUtil.isNotEmpty(baseRoleService.findResourceIdByEmployeeId(applicationId, employeeId));
    }

}
