package cn.slainc.tenant.lifecycle;

import java.util.ArrayList;
import java.util.List;

public class TenantDataTemplate {

    private List<String> roleCodes = new ArrayList<>();

    private List<String> permissionCodes = new ArrayList<>();

    private List<String> menus = new ArrayList<>();

    private List<String> dictTypes = new ArrayList<>();

    private List<String> initStatements = new ArrayList<>();

    public TenantDataTemplate() {
    }

    public TenantDataTemplate(TenantDataTemplate source) {
        if (source != null) {
            this.roleCodes = new ArrayList<>(source.getRoleCodes());
            this.permissionCodes = new ArrayList<>(source.getPermissionCodes());
            this.menus = new ArrayList<>(source.getMenus());
            this.dictTypes = new ArrayList<>(source.getDictTypes());
            this.initStatements = new ArrayList<>(source.getInitStatements());
        }
    }

    public boolean isEmpty() {
        return roleCodes.isEmpty() && permissionCodes.isEmpty() && menus.isEmpty()
                && dictTypes.isEmpty() && initStatements.isEmpty();
    }

    public List<String> getRoleCodes() {
        return roleCodes;
    }

    public void setRoleCodes(List<String> roleCodes) {
        this.roleCodes = roleCodes == null ? new ArrayList<>() : new ArrayList<>(roleCodes);
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public void setPermissionCodes(List<String> permissionCodes) {
        this.permissionCodes = permissionCodes == null ? new ArrayList<>() : new ArrayList<>(permissionCodes);
    }

    public List<String> getMenus() {
        return menus;
    }

    public void setMenus(List<String> menus) {
        this.menus = menus == null ? new ArrayList<>() : new ArrayList<>(menus);
    }

    public List<String> getDictTypes() {
        return dictTypes;
    }

    public void setDictTypes(List<String> dictTypes) {
        this.dictTypes = dictTypes == null ? new ArrayList<>() : new ArrayList<>(dictTypes);
    }

    public List<String> getInitStatements() {
        return initStatements;
    }

    public void setInitStatements(List<String> initStatements) {
        this.initStatements = initStatements == null ? new ArrayList<>() : new ArrayList<>(initStatements);
    }
}
