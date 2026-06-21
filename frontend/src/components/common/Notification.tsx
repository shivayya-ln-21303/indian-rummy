import { useEffect } from 'react';

interface Props {
  message: string;
  onClose: () => void;
}

export default function Notification({ message, onClose }: Props) {
  useEffect(() => {
    const timer = setTimeout(onClose, 3000);
    return () => clearTimeout(timer);
  }, [message]);

  return (
    <div className="notification" onClick={onClose}>
      {message}
    </div>
  );
}

