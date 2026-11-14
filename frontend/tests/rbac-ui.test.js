const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const readJson = relativePath => JSON.parse(fs.readFileSync(path.join(root, relativePath), 'utf8'));

function walk(value, visit) {
  if (!value || typeof value !== 'object') return;
  visit(value);
  if (Array.isArray(value)) {
    value.forEach(item => walk(item, visit));
  } else {
    Object.keys(value).forEach(key => walk(value[key], visit));
  }
}

function findAll(value, predicate) {
  const matches = [];
  walk(value, item => {
    if (predicate(item)) matches.push(item);
  });
  return matches;
}

const site = readJson('pages/site.json');
const templateList = readJson('pages/crud-list.json');
const templateView = readJson('pages/crud-view.json');
const accountList = readJson('pages/account-list.json');
const users = readJson('pages/user-management.json');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');

['TEMPLATE_READ', 'TRACE_READ', 'ACCOUNT_READ', 'INBOX_ACCESS', 'USER_MANAGE'].forEach(permission => {
  assert(findAll(site, node => node.requiredPermission === permission).length > 0,
    `菜单缺少 ${permission} 权限标记`);
});
assert(findAll(site, node => node.url === '/crud/:id/edit'
  && node.requiredPermission === 'TEMPLATE_WRITE').length === 1,
  '模板编辑路由缺少 TEMPLATE_WRITE');

['新增模板', '编辑', '复制'].forEach(label => {
  assert(findAll(templateList, node => node.label === label && node.requiredPermission === 'TEMPLATE_WRITE').length > 0,
    `${label} 缺少 TEMPLATE_WRITE`);
});
['测试', '启动', '停止'].forEach(label => {
  assert(findAll(templateList, node => node.label === label && node.requiredPermission === 'TEMPLATE_EXECUTE').length > 0,
    `${label} 缺少 TEMPLATE_EXECUTE`);
});
assert(findAll(templateView, node => node.label === '编辑' && node.requiredPermission === 'TEMPLATE_WRITE').length === 1);
assert(findAll(accountList, node => node.requiredPermission === 'ACCOUNT_WRITE').length >= 3);

const userText = JSON.stringify(users);
assert(userText.includes('/auth/users/roles'));
assert(userText.includes('post:${ls:backend_url}/auth/users'));
assert(userText.includes('/auth/users/${id}/password'));
assert(findAll(users, node => node.name === 'password'
  && node.validations && node.validations.minLength === 8).length === 2,
  '创建用户和重置密码都必须在前端校验至少 8 位');

assert(html.includes('const currentPermissions = new Set'));
assert(html.includes('fallbackPermissionsByRole'));
assert(html.includes("USER: ['INBOX_ACCESS']"));
assert(html.includes('filterAuthorizedSchema'));
assert(html.includes("error.response.status === 403"));
assert(html.includes('payload.msg || payload.message || payload.error'));
assert(html.includes("localStorage.setItem('currentRoleLabel'"));

console.log('RBAC UI checks passed.');
