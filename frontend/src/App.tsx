import { useEffect } from 'react';
import { useGameStore } from './store/gameStore';
import LobbyScreen   from './components/lobby/LobbyScreen';
import WaitingRoom   from './components/waiting/WaitingRoom';
import GameTable     from './components/game/GameTable';
import WinnerDialog  from './components/dialogs/WinnerDialog';
import Notification  from './components/common/Notification';

export default function App() {
  const { gameState, connectionStatus, connect, notification, clearNotification } = useGameStore();

  useEffect(() => {
    connect();
  }, []);

  const renderScreen = () => {
    if (connectionStatus !== 'connected') {
      return (
        <div className="splash">
          <div className="splash-logo">🃏</div>
          <h1>రమ్మీ పేకాట</h1>
          <p className="connecting-text">
            {connectionStatus === 'connecting' ? 'అనుసంధానం అవుతోంది…' : 'మళ్ళీ అనుసంధానం అవుతోంది…'}
          </p>
        </div>
      );
    }

    if (!gameState) return <LobbyScreen />;

    switch (gameState.status) {
      case 'WAITING_FOR_PLAYERS': return <WaitingRoom />;
      case 'DEALING':
      case 'PLAYING':
      case 'JOKER_UNLOCKED':     return <GameTable />;
      case 'FINISHED':           return <><GameTable /><WinnerDialog /></>;
      default:                   return <LobbyScreen />;
    }
  };

  return (
    <div className="app">
      {renderScreen()}
      {notification && (
        <Notification message={notification} onClose={clearNotification} />
      )}
    </div>
  );
}

