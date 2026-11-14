const assert = require('assert');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const pageDir = path.join(root, 'pages');

function readPage(name) {
  return JSON.parse(fs.readFileSync(path.join(pageDir, name), 'utf8'));
}

function walk(value, visit) {
  if (!value || typeof value !== 'object') return;
  visit(value);
  if (Array.isArray(value)) {
    value.forEach(item => walk(item, visit));
    return;
  }
  Object.keys(value).forEach(key => walk(value[key], visit));
}

function findAll(schema, predicate) {
  const result = [];
  walk(schema, node => {
    if (predicate(node)) result.push(node);
  });
  return result;
}

function findOne(schema, predicate, message) {
  const value = findAll(schema, predicate)[0];
  assert(value, message);
  return value;
}

fs.readdirSync(pageDir)
  .filter(name => name.endsWith('.json'))
  .forEach(readPage);

const accountNew = readPage('account-new.json');
const accountList = readPage('account-list.json');
const templateNew = readPage('crud-new.json');
const templateEdit = readPage('crud-edit.json');
const templateList = readPage('crud-list.json');
const templateView = readPage('crud-view.json');
const inbox = readPage('inbox.json');
const site = readPage('site.json');
const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const loginHtml = fs.readFileSync(path.join(root, 'login.html'), 'utf8');
const serverSource = fs.readFileSync(path.join(root, 'server.js'), 'utf8');

// 本地 API 统一走前端同源代理，旧浏览器缓存的 7777 地址会自动迁移。
assert(html.includes("localStorage.setItem('backend_url', '/api')"));
assert(loginHtml.includes("? '/api'"));
assert(serverSource.includes("app.use('/api'"));
assert(serverSource.includes("res.status(502).json"));

// 渠道必须来自后端，新增页不得维护硬编码渠道 options。
[accountNew, templateNew].forEach(schema => {
  const selector = findOne(schema, node => node.name === 'sendChannel', '缺少渠道选择器');
  assert(!selector.options, '新增页渠道选择器不能硬编码 options');
  assert(String(selector.source.url).includes('/channel/list'));
  assert(String(selector.source.url).includes('includeDisabled=false'));
});
assert(findAll(templateEdit, node => String(node.source && node.source.url).includes('/channel/list')).some(node => node.source.url.includes('includeDisabled=true')));
assert(findAll(templateView, node => String(node.source && node.source.url).includes('/channel/list')).some(node => node.source.url.includes('includeDisabled=true')));

const channelSource = findOne(templateNew, node => node.name === 'sendChannel').source;
const channelOptions = new Function('payload', channelSource.adaptor)({
  status: '0',
  msg: '操作成功',
  data: [
    {code: 10, name: 'IM', enabled: true},
    {code: 20, name: 'Push', enabled: false}
  ]
});
assert.deepStrictEqual(channelOptions.data.map(item => item.value), ['10']);

// 账号选项遵守 disabled/READY；短信去掉 AUTO，负数环境变量账号仍合法。
const accountSource = findOne(templateNew, node => node.name === 'sendAccount').source;
const accountAdaptor = new Function('payload', 'api', accountSource.adaptor);
const accountPayload = {
  status: '0',
  msg: '',
  data: [
    {label: 'AUTO', value: '0', disabled: false, availabilityStatus: 'READY'},
    {label: '环境变量账号', value: '-40', disabled: false, availabilityStatus: 'READY'},
    {label: '不可用账号', value: '2', disabled: true, availabilityStatus: 'NOT_CONFIGURED'},
    {label: 'Mock', value: '3', disabled: false, availabilityStatus: 'READY'}
  ]
};
const emailAccounts = accountAdaptor(JSON.parse(JSON.stringify(accountPayload)), {url: '/account/queryByChannelType?channelType=40'});
assert(emailAccounts.data.some(item => item.value === '-40'), '负数环境变量账号不能被过滤');
assert(emailAccounts.data.some(item => item.disabled === true), '非短信不可用账号应保留为禁选项');
const smsAccounts = accountAdaptor(JSON.parse(JSON.stringify(accountPayload)), {url: '/account/queryByChannelType?channelType=30'});
assert.deepStrictEqual(smsAccounts.data.map(item => item.value), ['-40', '3']);

// IM 保存格式固定账号 0，内容为 title/content/url JSON。
const templateSaveApi = findOne(templateNew, node => node.url === '${ls:backend_url}/messageTemplate/save');
const adaptTemplate = new Function('api', templateSaveApi.requestAdaptor);
const imRequest = adaptTemplate({data: {sendChannel: '10', sendAccount: '', title: '系统通知', content: '你好，{$name}', url: 'https://example.com/detail'}});
assert.strictEqual(imRequest.data.sendAccount, 0);
assert.deepStrictEqual(JSON.parse(imRequest.data.msgContent), {
  title: '系统通知',
  content: '你好，{$name}',
  url: 'https://example.com/detail'
});

// Webhook 模板 body 必须是对象或数组，并以字符串写入 msgContent。
const webhookRequest = adaptTemplate({data: {sendChannel: '130', sendAccount: '12', webhookBody: '{"event":"build.finished","project":"{$project}"}'}});
const webhookContent = JSON.parse(webhookRequest.data.msgContent);
assert.deepStrictEqual(JSON.parse(webhookContent.body), {event: 'build.finished', project: '{$project}'});
assert.throws(() => adaptTemplate({data: {sendChannel: '130', sendAccount: '12', webhookBody: '"text"'}}), /对象或数组/);

// Webhook 账号只允许 HTTPS、固定 POST，不允许敏感 Header。
const webhookUrl = findOne(accountNew, node => node.name === 'webhookUrl');
assert.strictEqual(webhookUrl.type, 'input-password');
assert.strictEqual(findAll(accountNew, node => node.name === 'method' || node.label === 'HTTP Method').length, 0);
const accountSaveApi = findOne(accountNew, node => node.url === '${ls:backend_url}/account/save');
const adaptAccount = new Function('api', accountSaveApi.requestAdaptor);
assert.throws(() => adaptAccount({data: {sendChannel: '130', webhookUrl: 'http://example.com', webhookHeaders: []}}), /https/);
assert.throws(() => adaptAccount({data: {sendChannel: '130', webhookUrl: 'https://example.com', webhookHeaders: [{key: 'Content-Type', value: 'x'}]}}), /Header/);
const savedWebhook = adaptAccount({data: {sendChannel: '130', webhookUrl: 'https://example.com/events', webhookHeaders: [{key: 'X-Api-Key', value: 'secret'}]}});
assert.deepStrictEqual(JSON.parse(savedWebhook.data.accountConfig), {webhook: 'https://example.com/events', headers: {'X-Api-Key': 'secret'}});

// 脱敏配置被拆成编辑字段，****** 必须原样回传。
const listApi = findOne(accountList, node => String(node.url).includes('/account/list'));
const adaptedRows = new Function('payload', listApi.adaptor)({
  status: '0',
  msg: '',
  data: [{id: 12, name: '构建通知', sendChannel: 130, accountConfig: '{"webhook":"******","headers":{"Authorization":"******"}}'}]
});
assert.strictEqual(adaptedRows.data.rows[0].webhookUrl, '******');
assert.deepStrictEqual(adaptedRows.data.rows[0].webhookHeaders, [{key: 'Authorization', value: '******'}]);
const editSaveApi = findOne(accountList, node => node.url === '${ls:backend_url}/account/save');
const editRequest = new Function('api', editSaveApi.requestAdaptor)({data: {
  sendChannel: 130,
  webhookUrl: '******',
  webhookHeaders: [{key: 'Authorization', value: '******'}]
}});
assert.deepStrictEqual(JSON.parse(editRequest.data.accountConfig), {webhook: '******', headers: {Authorization: '******'}});

// 历史模板不隐藏、不删除；不可用时禁止测试、启动和重新提交。
assert(String(findOne(templateList, node => node.type === 'crud').api).includes('/messageTemplate/list'));
assert.strictEqual(findAll(templateList, node => node.label === '删除' || node.label === '批量删除').length, 0);
['测试', '启动'].forEach(label => {
  const button = findOne(templateList, node => node.label === label, `缺少${label}按钮`);
  assert(String(button.disabledOn).includes('channelReady'));
});
const editSubmit = findOne(templateEdit, node => node.type === 'submit' && node.label === '保存');
assert(String(editSubmit.disabledOn).includes('channelReady'));
assert(findAll(templateView, node => node.name === 'msgContent').length > 0, '详情页必须展示历史模板内容');

// 收件箱 receiver 仅取登录用户，覆盖单条/批量/全部已读和删除。
assert(findAll(site, node => node.schemaApi === 'get:/pages/inbox.json').length === 1);
assert.strictEqual(findAll(site, node => node.schemaApi === 'get:/pages/material-upload.json').length, 0, '个人开发者版不应暴露企业渠道物料入口');
assert.strictEqual(findAll(inbox, node => node.name === 'receiver').length, 0, '收件箱不能让用户手填 receiver');
const inboxText = JSON.stringify(inbox);
assert(inboxText.includes('/inbox/messages?receiver=${ls:openId}'));
assert(inboxText.includes('/inbox/unread-count?receiver=${ls:openId}'));
assert(inboxText.includes('/messages/${id}/read?receiver=${ls:openId}'));
assert(inboxText.includes('/inbox/messages/read'));
assert(inboxText.includes('/inbox/messages/delete'));
assert(inboxText.includes('delete:${ls:backend_url}/inbox/messages/${id}?receiver=${ls:openId}'));

// 保存、测试、启动在全局请求层再次校验，错误只展示后端 msg，不记录敏感值。
assert(html.includes('/account/availability?'));
assert(html.includes("operation.kind === 'test' || operation.kind === 'start'"));
assert(html.includes("path.endsWith('/messageTemplate/save')"));
assert(html.includes("payload && payload.msg"));
assert(!/console\.(log|error|warn)\s*\(/.test(html));

console.log('Personal channel UI checks passed.');
