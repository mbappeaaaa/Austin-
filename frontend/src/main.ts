import { createApp } from 'vue'
import {
  Alert, Badge, Button, ConfigProvider, Descriptions, Drawer, Dropdown, Empty, Form, Input,
  InputNumber, Layout, List, Menu, Modal, Progress, Radio, Select, Skeleton,
  Space, Spin, Switch, Table, Tag,
} from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)
;[
  Alert, Badge, Button, ConfigProvider, Descriptions, Drawer, Dropdown, Empty, Form, Input,
  InputNumber, Layout, List, Menu, Modal, Progress, Radio, Select, Skeleton,
  Space, Spin, Switch, Table, Tag,
].forEach(plugin => app.use(plugin))
app.use(router).mount('#app')
