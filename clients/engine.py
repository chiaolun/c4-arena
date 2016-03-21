import abc


class Engine():
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def get_move(self, state, moves, side):
        pass

    def end_game(self, state, moves, side, winner):
        pass
