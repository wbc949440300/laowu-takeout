import { mount, createLocalVue } from '@vue/test-utils';
import VueRouter from 'vue-router';
import ElementUI from 'element-ui';
import Breadcrumb from '@/components/Breadcrumb/index.vue';

const localVue = createLocalVue();
localVue.use(VueRouter);
localVue.use(ElementUI);

const routes = [
    { 'path': '/dashboard', 'meta': { 'title': 'dashboard' } },
    {
        'path': '/menu',
        'meta': { 'title': 'menu' },
        'children': [{
            'path': 'menu1',
            'meta': { 'title': 'menu1' },
            'children': [{
                'path': 'menu1-1',
                'meta': { 'title': 'menu1-1' }
            },
            {
                'path': 'menu1-2',
                'redirect': 'noredirect',
                'meta': { 'title': 'menu1-2' },
                'children': [{
                    'path': 'menu1-2-1',
                    'meta': { 'title': 'menu1-2-1' }
                },
                {
                    'path': 'menu1-2-2'
                }]
            }]
        }]
    }];

describe('Breadcrumb.vue', () => {
    let router: VueRouter;

    const mountAt = async (path: string) => {
        router = new VueRouter({ routes });
        await router.push(path);
        const wrapper = mount(Breadcrumb, { localVue, router });
        await localVue.nextTick();
        return wrapper;
    };

    it('dashboard', async () => {
        const wrapper = await mountAt('/dashboard');
        const len = wrapper.findAll('.el-breadcrumb__inner').length;
        expect(len).toBe(1);
    });

    it('normal route', async () => {
        const wrapper = await mountAt('/menu/menu1');
        const len = wrapper.findAll('.el-breadcrumb__inner').length;
        expect(len).toBe(2);
    });

    it('nested route', async () => {
        const wrapper = await mountAt('/menu/menu1/menu1-2/menu1-2-1');
        const len = wrapper.findAll('.el-breadcrumb__inner').length;
        expect(len).toBe(4);
    });

    it('no meta.title', async () => {
        const wrapper = await mountAt('/menu/menu1/menu1-2/menu1-2-2');
        const len = wrapper.findAll('.el-breadcrumb__inner').length;
        expect(len).toBe(3);
    });

    it('click link', async () => {
        const wrapper = await mountAt('/menu/menu1/menu1-2/menu1-2-2');
        const breadcrumbArray = wrapper.findAll('.el-breadcrumb__inner');
        const second = breadcrumbArray.at(1);
        const href = second.find('a').text();
        expect(href).toBe('menu1');
    });

    it('noredirect', async () => {
        const wrapper = await mountAt('/menu/menu1/menu1-2/menu1-2-1');
        const breadcrumbArray = wrapper.findAll('.el-breadcrumb__inner');
        const redirectBreadcrumb = breadcrumbArray.at(2);
        expect(redirectBreadcrumb.contains('a')).toBe(false);
    });

    it('last breadcrumb', async () => {
        const wrapper = await mountAt('/menu/menu1/menu1-2/menu1-2-1');
        const breadcrumbArray = wrapper.findAll('.el-breadcrumb__inner');
        const redirectBreadcrumb = breadcrumbArray.at(3);
        expect(redirectBreadcrumb.contains('a')).toBe(false);
    });
});
