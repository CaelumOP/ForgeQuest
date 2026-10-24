import type { FunctionComponent } from 'preact'
import { AiCheck } from './dev/AiCheck'
import { KitGallery } from './dev/KitGallery'
import { CoachScreen } from './ui/coach/CoachScreen'
import { FoodScreen } from './ui/food/FoodScreen'
import { ScanScreen } from './ui/food/ScanScreen'
import { HistoryScreen } from './ui/history/HistoryScreen'
import { SessionScreen } from './ui/history/SessionScreen'
import { HomeScreen } from './ui/home/HomeScreen'
import { LiveWorkoutScreen } from './ui/live/LiveWorkoutScreen'
import { ProfileScreen } from './ui/profile/ProfileScreen'
import { BadgesScreen } from './ui/progress/BadgesScreen'
import { ProgressScreen } from './ui/progress/ProgressScreen'
import { AboutScreen } from './ui/screens'
import { ExerciseDetailScreen } from './ui/workouts/ExerciseDetailScreen'
import { LibraryScreen } from './ui/workouts/LibraryScreen'
import { PlanEditorScreen } from './ui/workouts/PlanEditorScreen'
import { WorkoutsScreen } from './ui/workouts/WorkoutsScreen'

// Every screen and its route. `#/exercise/back_squat` gives ExerciseDetailScreen param "back_squat".
// Screen workers edit only their own screen files, never this table.
type Screen = FunctionComponent<{ param?: string }>
export type Route = { title: string; Screen: Screen; tab?: boolean; bare?: boolean }

export const routes: Record<string, Route> = {
  '/': { title: 'tab_home', Screen: HomeScreen, tab: true },
  '/workouts': { title: 'tab_workouts', Screen: WorkoutsScreen, tab: true },
  '/food': { title: 'tab_food', Screen: FoodScreen, tab: true },
  '/coach': { title: 'tab_coach', Screen: CoachScreen, tab: true },
  '/profile': { title: 'tab_profile', Screen: ProfileScreen, tab: true },
  '/live': { title: 'tab_workouts', Screen: LiveWorkoutScreen, bare: true },
  '/plan': { title: 'tab_workouts', Screen: PlanEditorScreen },
  '/library': { title: 'tab_workouts', Screen: LibraryScreen },
  '/exercise': { title: 'tab_workouts', Screen: ExerciseDetailScreen },
  '/history': { title: 'tab_workouts', Screen: HistoryScreen },
  '/session': { title: 'tab_workouts', Screen: SessionScreen },
  '/progress': { title: 'tab_workouts', Screen: ProgressScreen },
  '/badges': { title: 'tab_profile', Screen: BadgesScreen },
  '/scan': { title: 'tab_food', Screen: ScanScreen, bare: true },
  '/about': { title: 'settings_group_about', Screen: AboutScreen },
  '/check': { title: 'screen_ai_check', Screen: AiCheck as Screen },
  '/kit': { title: 'screen_ai_check', Screen: KitGallery as Screen },
}

/** "/exercise/back_squat" → the "/exercise" route and param "back_squat". */
export function matchRoute(path: string): { route?: Route; param?: string } {
  if (routes[path]) return { route: routes[path] }
  const cut = path.indexOf('/', 1)
  if (cut < 0) return {}
  return { route: routes[path.slice(0, cut)], param: decodeURIComponent(path.slice(cut + 1)) }
}
