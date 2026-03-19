import { Routes } from '@angular/router';

export const routes: Routes = [
	{
		path: '',
		loadComponent: () =>
			import('./dashboard/dashboard.component').then((module) => module.DashboardComponent)
	},
	{
		path: '**',
		redirectTo: ''
	}
];
