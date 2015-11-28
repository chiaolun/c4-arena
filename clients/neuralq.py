#!/usr/bin/env python

from keras.models import Sequential
from keras.layers.core import Dense, Dropout, Activation
from keras.optimizers import RMSprop
import numpy as np
import random
import os.path

ncols = 7
nrows = 6
state_dim = ncols * nrows

# http://outlace.com/Reinforcement-Learning-Part-3/

def standardize(state):
    N = len(state)
    standard_state = np.zeros((N,2))
    for i, x in enumerate(state):
        if x == 0:
            continue
        elif x == 1:
            standard_state[i,0] = 1.
        else:
            standard_state[i,1] = 1.
    return standard_state.reshape(1,state_dim*2)

def valid_columns(state):
    return [j for j in range(ncols)
            if any(all([state[0, 2 * i + k] == 0 for k in range(2)])
                   for i in range(j * nrows, (j + 1) * nrows))]

class NeuralQ():
    def __init__(self, epsilon = 0.01, gamma = 1., save_interval = 100):
        self.epsilon = epsilon
        self.gamma = gamma
        self.save_interval = save_interval
        self.epoch = 0
        self.replay = []
        self.memory_size = 100
        self.batch_size = 50

        self.models = {}
        for side in [1,2]:
            model = Sequential()
            model.add(Dense(80, init='lecun_uniform', input_shape=(state_dim*2,)))
            model.add(Activation('relu'))
            # model.add(Dropout(0.5))

            # model.add(Dense(20, init='lecun_uniform'))
            # model.add(Activation('relu'))
            # # model.add(Dropout(0.5))

            model.add(Dense(ncols, init='lecun_uniform'))
            model.add(Activation('linear')) #linear output so we can have range of real-valued outputs

            rms = RMSprop()
            model.compile(loss='mse', optimizer=rms)

            if os.path.isfile("model_{side}.dat".format(side=side)):
                model.load_weights("model_{side}.dat".format(side=side))

            self.models[side] = model

    def get_move(self, state, side):
        model = self.models[side]
        gamma = self.gamma
        epsilon = self.epsilon
        memory_size = self.memory_size
        batch_size = self.batch_size

        state = standardize(state)
        state = np.array(state)
        #We are in state S
        #Let's run our Q function on S to get Q values for all possible actions
        qval = model.predict(state, batch_size=1)
        qval_allowed = np.empty(qval.shape)
        qval_allowed[:] = np.NAN
        valids = valid_columns(state)
        for i in valids:
            qval_allowed[0,i] = qval[0,i]

        if (random.random() < epsilon): #choose random action
            action = np.random.choice(valids)
        elif side == 1: #choose best action from Q(s,a) values
            action = (np.nanargmax(qval_allowed))
        else:
            action = (np.nanargmin(qval_allowed))

        def observe_reward(reward=0, new_state=None):
            if side != 1:
                reward *= -1
            if new_state:
                new_state = standardize(new_state)
                new_state = np.array(new_state)

            self.replay.append((state, action, reward, new_state))
            if len(self.replay) > memory_size:
                self.replay.pop(0)
            else:
                # Don't start training until you have enough samples
                return

            minibatch = random.sample(self.replay, batch_size)

            X_train = []
            y_train = []

            for old_state, action0, reward, new_state in minibatch:
                old_qval = model.predict(old_state, batch_size=1)

                # This function observes the reward after the move chosen
                y = np.zeros((1,ncols))
                y[:] = old_qval[:]

                if new_state is None:
                    # Terminal state
                    update = reward
                else:
                    # Non-terminal state

                    #Get max_Q(S',a)
                    newQ = model.predict(new_state, batch_size=1)
                    maxQ = np.max(newQ)

                    update = (reward + (gamma * maxQ))

                y[0][action0] = update #target output
                X_train.append(old_state.reshape(state_dim*2,))
                y_train.append(y.reshape(ncols,))

            X_train = np.array(X_train)
            y_train = np.array(y_train)
            model.fit(X_train, y_train, batch_size=batch_size, nb_epoch=1, verbose=1)
            self.epoch += 1
            if self.epoch % self.save_interval == 0:
                model.save_weights("model_{side}.dat".format(side=side), overwrite=True)

        return action, observe_reward
